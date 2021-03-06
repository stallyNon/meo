(ns meo.electron.renderer.ui.re-frame
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as rc]
            [re-frame.core :refer [reg-sub subscribe]]
            [re-frame.db :as rdb]
            [meo.electron.renderer.ui.menu :as menu]
            [meo.electron.renderer.ui.heatmap :as hm]
            [meo.electron.renderer.ui.grid :as g]
            [meo.electron.renderer.ui.new-entries :as n]
            [meo.electron.renderer.ui.stats :as stats]
            [meo.electron.renderer.ui.footer :as f]
            [meo.electron.renderer.ui.charts.award :as aw]
            [meo.electron.renderer.ui.dashboard :as db]
            [meo.electron.renderer.ui.config :as cfg]
            [meo.electron.renderer.ui.sync :as sync]
            [meo.electron.renderer.ui.charts.custom-fields :as cf2]
            [meo.electron.renderer.ui.charts.correlation :as corr]
            [meo.electron.renderer.ui.charts.location :as loc]
            [meo.electron.renderer.ui.charts.time.durations :as cd]
            [meo.electron.renderer.ui.entry.briefing.calendar :as cal]
            [meo.electron.renderer.ui.entry.briefing :as b]
            [meo.electron.renderer.ui.data-explorer :as dex]))

;; Subscription Handlers
(reg-sub :gql-res (fn [db _] (:gql-res db)))
(reg-sub :db (fn [db _] db))
(reg-sub :stories (fn [db _]
                    (->> (get-in db [:gql-res :options :data :stories])
                         (map (fn [x] [(:timestamp x) x]))
                         (into {}))))
(reg-sub :sagas (fn [db _]
                  (->> (get-in db [:gql-res :options :data :sagas])
                       (map (fn [x] [(:timestamp x) x]))
                       (into {}))))

(reg-sub :briefings (fn [db _]
                      (->> (get-in db [:gql-res :options :data :briefings])
                           (map (fn [m] [(:day m) (:timestamp m)]))
                           (into {}))))

(reg-sub :options (fn [db _] (:options db)))
(reg-sub :imap-status (fn [db _] (:imap-status db)))
(reg-sub :imap-cfg (fn [db _] (:imap-cfg db)))
(reg-sub :custom-field-stats (fn [db _] (:custom-field-stats db)))
(reg-sub :git-stats (fn [db _] (:git-commits db)))
(reg-sub :last-update (fn [db _] (:last-update (:query-cfg db))))
(reg-sub :startup-progress (fn [db _] (:startup-progress db)))
(reg-sub :running-pomodoro (fn [db _] (:running (:pomodoro db))))
(reg-sub :story-predict (fn [db _] (:story-predict db)))
(reg-sub :current-page (fn [db _] (:current-page db)))
(reg-sub :show-pvt (fn [db _] (:show-pvt (:cfg db))))
(reg-sub :cal-day (fn [db _] (-> db :cfg :cal-day)))
(reg-sub :busy-status (fn [db _] (:busy-status db)))
(reg-sub :query-cfg (fn [db _] (:query-cfg db)))
(reg-sub :widgets (fn [db _] (:widgets (:cfg db))))
(reg-sub :questionnaires (fn [db _] (:questionnaires (:options db))))
(reg-sub :dashboards (fn [db _] (:dashboards (:questionnaires (:options db)))))
(reg-sub :active-dashboard (fn [db _] (:active (:dashboard (:cfg db)))))
(reg-sub :entries-map (fn [db _] (:entries-map db)))
(reg-sub :results (fn [db _] (:results db)))
(reg-sub :new-entries (fn [db _] (:new-entries db)))
(reg-sub :cfg (fn [db _] (:cfg db)))
(reg-sub :locale (fn [db _] (:locale (:cfg db) :en)))
(reg-sub :backend-cfg (fn [db _] (:backend-cfg db)))
(reg-sub :repos (fn [db _] (:repos (:backend-cfg db))))
(reg-sub :stats (fn [db _] (:stats db)))
(reg-sub :timing (fn [db _] (:timing db)))
(reg-sub :geo-photos (fn [db _] (:geo-photos db)))
(reg-sub :chart-data (fn [db _] (select-keys db [:media-stats])))

(defn main-page [put-fn]
  (let [cfg (subscribe [:cfg])
        single-column (reaction (:single-column @cfg))]
    (fn [put-fn]
      [:div.flex-container
       [:div.grid
        [:div.wrapper.col-3
         [menu/menu-view put-fn]
         [:div.cal
          [cal/calendar-view put-fn]]
         [b/briefing-column-view :briefing put-fn]
         [:div {:class (if @single-column "single" "left")}
          [g/tabs-view :left put-fn]]
         (when-not @single-column
           [:div.right
            [g/tabs-view :right put-fn]])
         [f/footer put-fn]]]
       [stats/stats-text]])))

(defn charts-page [put-fn]
  [:div.flex-container
   [:div.charts-grid
    [:div.wrapper
     [cd/durations-bar-chart 200 5 put-fn]]]])

(defn countries-page [put-fn]
  [:div.flex-container
   [loc/location-chart]])

(defn cal [put-fn]
  [:div.flex-container
   [cal/calendar-view put-fn]])

(defn load-progress [put-fn]
  (let [startup-progress (subscribe [:startup-progress])]
    (fn [put-fn]
      (let [startup-progress @startup-progress
            percent (Math/floor (* 100 startup-progress))]
        [:div.loader
         [:div.content
          [:h1 "starting meo..."]
          [:div.meter
           [:span {:style {:width (str percent "%")}}]]]]))))

(defn re-frame-ui [put-fn]
  (let [current-page (subscribe [:current-page])
        startup-progress (subscribe [:startup-progress])
        cfg (subscribe [:cfg])
        data-explorer (reaction (:data-explorer @cfg))]
    (fn [put-fn]
      (let [current-page @current-page
            startup-progress @startup-progress]
        (when-not @data-explorer
          (aset js/document "body" "style" "overflow" "hidden"))
        (if (= 1 startup-progress)
          [:div
           (case (:page current-page)
             :config [cfg/config put-fn]
             :sync [sync/sync put-fn]
             :charts-1 [charts-page put-fn]
             :countries [countries-page put-fn]
             :calendar [cal put-fn]
             :correlation [corr/scatter-matrix put-fn]
             :heatmap [hm/heatmap put-fn]
             :empty [:div.flex-container]
             [main-page put-fn])
           (when @data-explorer
             [dex/data-explorer])]
          [load-progress put-fn])))))

(defn state-fn [put-fn]
  (rc/render [re-frame-ui put-fn] (.getElementById js/document "reframe"))
  {:observed rdb/app-db})

(defn cmp-map [cmp-id]
  {:cmp-id   cmp-id
   :state-fn state-fn})
