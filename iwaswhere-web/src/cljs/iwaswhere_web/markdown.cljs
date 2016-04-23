(ns iwaswhere-web.markdown
  (:require [markdown.core :as md]
            [iwaswhere-web.helpers :as h]
            [matthiasn.systems-toolbox-ui.helpers :as uh]
            [clojure.string :as s]
            [cljsjs.moment]
            [cljs.pprint :as pp]))

(defn hashtags-replacer
  "Replaces hashtags in entry text. Depending on show-hashtags? switch either displays
  the hashtag or not."
  [show-hashtags?]
  (fn [acc hashtag]
    (let [f-hashtag (if show-hashtags? hashtag (subs hashtag 1))]
      (s/replace acc (re-pattern (str "[^*]" hashtag "(?!\\w)")) (str " **" f-hashtag "**")))))

(defn mentions-replacer
  "Replaces mentions in entry text."
  [acc mention]
  (s/replace acc mention (str "**_" mention "_**")))

(defn- reducer
  "Generic reducer, allows calling specified function for each item in the collection."
  [text coll fun]
  (reduce fun text coll))

(defn markdown-render
  "Renders a markdown div using :dangerouslySetInnerHTML. Not that dangerous here since
  application is only running locally, so in doubt we could only harm ourselves.
  Returns nil when entry does not contain markdown text."
  [entry show-hashtags?]
  (when-let [md-string (:md entry)]
    (let [formatted-md (-> md-string
                           (reducer (:tags entry) (hashtags-replacer show-hashtags?))
                           (reducer (:mentions entry) mentions-replacer))]
      [:div {:dangerouslySetInnerHTML {:__html (md/md->html formatted-md)}}])))

(defn editable-md-render
  "Renders markdown in a pre>code element, with editable content. Sends update message to store
  component on any change to the component. The save button sends updated entry to the backend."
  [entry put-fn]
  (let [md-string (or (:md entry) "edit here and then save")
        ts (:timestamp entry)
        get-content #(aget (.. % -target -parentElement -parentElement -firstChild -firstChild) "innerText")
        update-temp-fn (fn [ev] (let [updated (merge entry (h/parse-entry (get-content ev)))]
                                  (put-fn [:update/temp-entry {:timestamp ts :updated updated}])))
        save-fn (fn [ev] (put-fn [:text-entry/update (merge entry (h/parse-entry (get-content ev)))]))]
    [:div.edit-md
     [:pre [:code {:content-editable true :on-input update-temp-fn} md-string]]
     [:button.pure-button.button-xsmall.pure-button-primary {:on-click save-fn} [:span.fa.fa-floppy-o] " save"]]))

(defn md-render
  "Helper for conditionally either showing rendered output or editable markdown."
  [entry put-fn editable? show-hashtags?]
  (if editable? (editable-md-render entry put-fn)
                (markdown-render entry show-hashtags?)))