(ns cities.frontend.views
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.frontend.misc :refer [pretty]]
            [cities.frontend.views.error :as error-view]
            [cities.frontend.views.game :as game-view]
            [cities.frontend.views.menu :as menu-view]
            [cities.frontend.views.round-over :as round-over-view]
            [cities.frontend.views.toast :refer [toast]]
            [taoensso.timbre :as log]))

(def app-title "Lost Cities")

(defn button
  [label on-click]
  [:button
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
   body
   [toast]])

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
