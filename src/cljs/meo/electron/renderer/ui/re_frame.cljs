(ns meo.electron.renderer.ui.re-frame
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as rc]
            [meo.electron.renderer.ui.menu :as menu]
            [re-frame.core :refer [reg-sub subscribe]]
            [meo.electron.renderer.ui.grid :as g]
            [meo.electron.renderer.ui.new-entries :as n]
            [meo.electron.renderer.ui.stats :as stats]
            [re-frame.db :as rdb]
            [meo.electron.renderer.ui.charts.award :as aw]
            [meo.electron.renderer.ui.dashboard :as db]
            [meo.electron.renderer.ui.charts.custom-fields :as cf2]
            [meo.electron.renderer.ui.charts.correlation :as corr]
            [meo.electron.renderer.ui.charts.location :as loc]
            [meo.electron.renderer.ui.charts.time.durations :as cd]
            [meo.electron.renderer.ui.entry.briefing.calendar :as cal]
            [reagent.core :as r]))

;; Subscription Handlers
(reg-sub :custom-field-stats (fn [db _] (:custom-field-stats db)))
(reg-sub :last-update (fn [db _] (:last-update (:query-cfg db))))
(reg-sub :options (fn [db _] (:options db)))
(reg-sub :current-page (fn [db _] (:current-page db)))
(reg-sub :stories (fn [db _] (:stories (:options db))))
(reg-sub :sagas (fn [db _] (:sagas (:options db))))
(reg-sub :busy (fn [db _] (:busy db)))
(reg-sub :busy-color (fn [db _] (:busy-color db)))
(reg-sub :query-cfg (fn [db _] (:query-cfg db)))
(reg-sub :widgets (fn [db _] (:widgets (:cfg db))))
(reg-sub :questionnaires (fn [db _] (:questionnaires (:options db))))
(reg-sub :dashboards (fn [db _] (:dashboards (:questionnaires (:options db)))))
(reg-sub :active-dashboard (fn [db _] (:active (:dashboard (:cfg db)))))
(reg-sub :entries-map (fn [db _] (:entries-map db)))
(reg-sub :results (fn [db _] (:results db)))
(reg-sub :new-entries (fn [db _] (:new-entries db)))
(reg-sub :combined-entries (fn [db _] (merge (:entries-map db) (:new-entries db))))
(reg-sub :cfg (fn [db _] (:cfg db)))
(reg-sub :planning-mode (fn [db _] (:planning-mode (:cfg db))))
(reg-sub :stats (fn [db _] (:stats db)))
(reg-sub :briefings (fn [db _] (:briefings db)))
(reg-sub :started-tasks (fn [db _] (:started-tasks db)))
(reg-sub :waiting-habits (fn [db _] (:waiting-habits db)))
(reg-sub :timing (fn [db _] (:timing db)))
(reg-sub :chart-data (fn [db _]
                       (select-keys db [:pomodoro-stats
                                        :task-stats
                                        :wordcount-stats
                                        :media-stats])))

(defn footer [put-fn]
  (let [cfg (subscribe [:cfg])
        dashboard-banner (reaction (:dashboard-banner @cfg))
        local (r/atom {:height 200})
        dashboards (subscribe [:dashboards])
        active-dashboard (subscribe [:active-dashboard])
        increase-height #(swap! local update-in [:height] + 5)
        decrease-height #(swap! local update-in [:height] - 5)
        select (fn [ev]
                 (let [sel (keyword (-> ev .-nativeEvent .-target .-value))]
                   (put-fn [:cmd/assoc-in
                            {:path  [:cfg :dashboard :active]
                             :value sel}])))]
    (fn [put-fn]
      [:div.footer
       [:div {:style {:max-height (when @dashboard-banner
                                    (str (:height @local) "px"))}}
        [db/dashboard put-fn]
        [:div
         [:select {:value     (or @active-dashboard "")
                   :on-change select}
          (for [dashboard-id (keys @dashboards)]
            ^{:key dashboard-id}
            [:option {:value dashboard-id} (name dashboard-id)])]
         (when @dashboard-banner
           [:span.fa.fa-plus-square {:on-click increase-height}])
         (when @dashboard-banner
           [:span.fa.fa-minus-square {:on-click decrease-height}])
         [stats/stats-text]]]])))

(defn main-page [put-fn]
  (let [cfg (subscribe [:cfg])
        planning-mode (subscribe [:planning-mode])
        single-column (reaction (:single-column @cfg))]
    (fn [put-fn]
      [:div.flex-container
       [:div.grid
        [:div.wrapper {:class (when @planning-mode "col-3")}
         [menu/menu-view put-fn]
         (when @planning-mode
           [:div.briefing
            [g/tabs-view :briefing put-fn]])
         [:div {:class (if @single-column "single" "left")}
          [g/tabs-view :left put-fn]]
         (when-not @single-column
           [:div.right
            [g/tabs-view :right put-fn]])
         [footer put-fn]]]
       [n/new-entries-view put-fn]])))

(defn charts-page [put-fn]
  [:div.flex-container
   [:div.charts-grid
    [:div.wrapper
     [cd/durations-bar-chart 200 5 put-fn]
     [cf2/custom-fields-chart put-fn]
     [aw/award-points put-fn]
     [stats/stats-view put-fn]]]])

(defn countries-page [put-fn]
  [:div.flex-container
   [loc/location-chart]])

(defn dashboards [put-fn]
  [:div.flex-container
   [db/dashboard put-fn]])

(defn cal [put-fn]
  [:div.flex-container
   [cal/calendar-view put-fn]])

(defn re-frame-ui [put-fn]
  (let [current-page (subscribe [:current-page])]
    (fn [put-fn]
      (let [current-page @current-page]
        (case (:page current-page)
          :dashboards [dashboards put-fn]
          :charts-1 [charts-page put-fn]
          :countries [countries-page put-fn]
          :calendar [cal put-fn]
          :correlation [corr/scatter-matrix put-fn]
          :empty [:div.flex-container]
          [main-page put-fn])))))

(defn state-fn [put-fn]
  (rc/render [re-frame-ui put-fn] (.getElementById js/document "reframe"))
  {:observed rdb/app-db})

(defn cmp-map [cmp-id]
  {:cmp-id   cmp-id
   :state-fn state-fn})
