(ns cities.client.views
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.client.misc :refer [pretty]]
            [cities.client.views.error :as error-view]
            [cities.client.views.game :as game-view]
            [cities.client.views.menu :as menu-view]
            [cities.client.views.round-over :as round-over-view]
            [cities.client.views.toast :refer [toast]]
            [taoensso.timbre :as log]))

(def app-title "Lost Cities")

(defn button
  [label on-click]
  [:button.mdl-button.mdl-button--raised
   {:value label
    :on-click  on-click}
   label])

(defn stack-table
  [stacks show-scores?]
  (letfn [(th [[color _]]
            [:th {:key color :class (name color)} (name color)])
          (tf [[color stack]]
            [:td {:key color :class (name color)}
            (score/expedition-score stack)])
          (td [row [color cards]]
            (if-let [card (get cards row)]
              [:td {:key color
                    :class (name color)}
               (card/label card)]
              [:td {:key color} ""]))
          (tr [row]
            [:tr {:key row}
             (map (partial td row) stacks)])]
    (let [max-count (->> (map (comp count val) stacks)
                         (apply max))
          keys (keys stacks)]
      [:table
       [:thead [:tr (map th stacks)]]
       (when show-scores? [:tfoot [:tr (map tf stacks)] ])
       [:tbody (map tr (range 0 max-count))]])))

(defn expedition-score-table
  [expeditions]
  (stack-table expeditions true))

(defn expedition-score-tables
  [round player opponent]
  (let [all-data (::game/player-data round)
        player-expeditions (get-in all-data [player ::player/expeditions])
        opponent-expeditions (get-in all-data [opponent ::player/expeditions])]
    [:div
     [:h5 "Your expeditions"]
     [expedition-score-table player-expeditions]
     [:h5 opponent "'s expeditions"]
     [expedition-score-table opponent-expeditions]]))

(defn game-over []
  (log/debug "Rendering game over screen!")
  (let [player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        past-rounds @(rf/subscribe [:past-rounds])
        round-scores (map score/round-scores past-rounds)
        player-score (reduce + (map #(get % player) round-scores))
        opponent-score (reduce + (map #(get % opponent) round-scores))]
    [:div
     [:h3 "Game Over"]
     [:table
      [:thead [:tr [:th player] [:th opponent]]]
      [:tfoot [:tr [:td player-score] [:td opponent-score]]]
      [:tbody (map-indexed
               (fn [index round]
                 [:tr {:key index}
                  [:td (get round player)]
                  [:td (get round opponent)]])
               round-scores)]]
     [:ul
      (map-indexed
       (fn [index round]
         [:li {:key index}
          (str "Round #" (inc index))
          (expedition-score-tables round player opponent)])
       past-rounds)]]))

(defn container
  [body]
  [:div
   [:div.mdl-demo-navigation
    [mdl/layout
     :fixed-header? true
     :children
     [[mdl/layout-header
       :attr {:style {:color "white"}}
       :children
       [[mdl/layout-header-row
         :children
         [[mdl/layout-title
           :children [[:a {:style {"cursor" "pointer"
                                   "color" "white"}
                           :on-click #(rf/dispatch [:show-menu])}
                       app-title]]]]]]]
      [mdl/layout-drawer
       :children
       [[mdl/layout-title
         :label app-title
         :attr {:on-click #(rf/dispatch [:show-menu])}]]]
      [mdl/layout-content
       :attr {:style {:background "white"}}
       :children [body
                  [toast]]]]]]])

(defn app []
  (let [screen @(rf/subscribe [:screen])]
    (case screen
      :game [game-view/container]
      :round-over [round-over-view/container]
      :game-over [container [game-over]]
      :menu [container [menu-view/menu]]
      :error [container [error-view/error]]
      (throw (js/Error. (str "Invalid screen: " screen))))))

(defn initialization-error
  [body]
  [:div
   [:h5 "Initialization error!"]
   [:textarea
    {:rows "100"
     :cols "100"
     :value (pretty body)}]])
