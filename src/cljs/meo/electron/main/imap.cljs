(ns meo.electron.main.imap
  "Component for encrypting and decrypting log files."
  (:require [taoensso.timbre :refer-macros [info debug error warn]]
            [meo.electron.main.runtime :as rt]
            [fs :refer [existsSync readFileSync mkdirSync writeFileSync statSync]]
            [child_process :refer [spawn]]
            [meo.electron.main.utils.encryption :as mue]
            [imap :as imap]
            [clojure.data :as data]
            [buildmail :as BuildMail]
            [cljs.reader :as edn]
            [clojure.pprint :as pp]))

(def data-path (:data-path rt/runtime-info))
(def repo-dir (:repo-dir rt/runtime-info))
(def cfg-path (str data-path "/imap.edn"))

(defn pp-str [x]
  (binding [pp/*print-right-margin* 100]
    (with-out-str (pp/pprint x))))

(defn imap-cfg []
  (when (existsSync cfg-path)
    (edn/read-string (readFileSync cfg-path "utf-8"))))

(defn imap-open [mailbox-name open-mb-cb]
  (when-let [cfg (imap-cfg)]
    (try
      (info "imap-open" mailbox-name)
      (let [mb (imap. (clj->js (:server cfg)))]
        (.once mb "ready" #(.openBox mb mailbox-name false (partial open-mb-cb mb)))
        (.once mb "error" #(error "IMAP connection" %))
        (.once mb "end" #(info "IMAP connection ended"))
        (.connect mb))
      (catch :default e (error e))))
  {})

(defn read-mailbox [[k mb-cfg] cmp-state put-fn]
  (let [{:keys [secret mailbox]} mb-cfg
        body-cb (fn [buffer seqn stream stream-info]
                  (let [end-cb (fn []
                                 (let [hex-body (mue/extract-body (apply str @buffer))]
                                   (debug "end-cb buffer" seqn "- size" (count hex-body))
                                   (when-let [decrypted (mue/decrypt-aes-hex hex-body secret)]
                                     (info "IMAP body end" seqn "- decrypted size" (count (str decrypted)))
                                     (put-fn [:entry/sync decrypted]))))]
                    (info "IMAP body stream-info" (js->clj stream-info))
                    (.on stream "data" #(let [s (.toString % "UTF8")]
                                          (when (= "TEXT" (.-which stream-info))
                                            (swap! buffer conj s))
                                          (info "IMAP body data seqno" seqn "- size" (count s))))
                    (.once stream "end" end-cb)))
        msg-cb (fn [msg seqn]
                 (let [buffer (atom [])]
                   (.on msg "body" (partial body-cb buffer seqn))
                   (.once msg "end" #(debug "IMAP msg end" seqn))))
        mb-cb (fn [mb err box]
                (try
                  (let [path [:sync :read k :last-read]
                        last-read (get-in (imap-cfg) path 0)
                        uid (str (inc last-read) ":*")
                        s (clj->js ["UNDELETED" ["UID" uid]])
                        cb (fn [err res]
                             (let [parsed-res (js->clj res)]
                               (when (and (seq parsed-res) (> (last parsed-res) last-read))
                                 (let [last-read (last parsed-res)
                                       f (.fetch mb res (clj->js {:bodies ["TEXT"]
                                                                  :struct true}))
                                       cb (fn []
                                            (let [cfg (assoc-in (imap-cfg) path last-read)
                                                  s (pp-str cfg)]
                                              (info "mb-cb fetch end, last-read" last-read)
                                              (writeFileSync cfg-path s)
                                              (.end mb)))]
                                   (info "search fetch" res)
                                   (.on f "message" msg-cb)
                                   (.once f "error" #(error "Fetch error" %))
                                   (.once f "end" cb)))))]
                    (info "search" mailbox s)
                    (.search mb s cb))
                  (catch :default e (error e))))]
    (imap-open mailbox mb-cb)))

(defn read-email [{:keys [put-fn cmp-state]}]
  (doseq [mb-tuple (:read (:sync (imap-cfg)))]
    (read-mailbox mb-tuple cmp-state put-fn))
  {})

(defn write-email [{:keys [msg-payload]}]
  (when-let [mb-cfg (:write (:sync (imap-cfg)))]
    (imap-open
      (:mailbox mb-cfg)
      (fn [mb _err _box]
        ;(.getBoxes mb (fn [err boxes] (.log js/console boxes)))
        (try (let [secret (:secret mb-cfg)
                   cipher-hex (mue/encrypt-aes-hex (pr-str msg-payload) secret)
                   decrypted (mue/decrypt-aes-hex cipher-hex secret)
                   _ (when-not (= msg-payload decrypted)
                       (warn "not equal" (data/diff msg-payload decrypted)))
                   append-cb (fn [err]
                               (if err
                                 (error "IMAP write" err)
                                 (info "IMAP wrote message"))
                               (info "closing WRITE connection")
                               (.end mb))
                   cb (fn [_err rfc-2822]
                        (debug "RFC2822\n" rfc-2822)
                        (.append mb rfc-2822 append-cb))]
               (-> (BuildMail. "text/plain")
                   (.setContent cipher-hex)
                   (.setHeader "subject" (str (:timestamp msg-payload) " " (:vclock msg-payload)))
                   (.build cb)))
             (catch :default e (error e))))))
  {})

(defn start-sync [{:keys [current-state put-fn]}]
  (info "starting IMAP sync")
  {:emit-msg [:cmd/schedule-new {:timeout (* 20 1000)
                                 :id      :imap-schedule
                                 :message [:sync/read-imap]
                                 :initial true
                                 :repeat  true}]})

(defn cmp-map [cmp-id]
  {:cmp-id      cmp-id
   :state-fn    (fn [_put-fn] {:state (atom {})})
   :opts        {:in-chan  [:buffer 100]
                 :out-chan [:buffer 100]}
   :handler-map {:sync/imap       write-email
                 :sync/start-imap start-sync
                 :sync/read-imap  read-email}})