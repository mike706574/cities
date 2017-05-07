(ns milo.client.views.hand
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [taoensso.timbre :as log]))

(defn rank [card] (if (card/wager? card) "W" (str (:milo.card/number card))))

(defn active []
  (log/info "Rendering hand.")
  (let [hand @(rf/subscribe [:hand])
        selected-card @(rf/subscribe [:card])
        destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])
        source @(rf/subscribe [:source])
        turn? @(rf/subscribe [:turn?])]
    [:div.mdl-grid
     [:div.hand.mdl-cell.mdl-cell--12-col
      (concat
       (map-indexed
        (fn [index card]
          (let [num (inc index)]
            (if (and destination (= card selected-card))
              (when source
                (if (= source :draw-pile)
                  [:div
                   {:key "drawn-card"
                    :class (str "card card-highlighted purple hand-" num)}
                   [:p "D"]]
                  (let [drawn-card (first (filter #(= (:milo.card/color %) source) available-discards))
                        rank (rank drawn-card)]
                    [:div
                     {:key "drawn-card"
                      :class (str "card card-highlighted " (name source) " rank-" rank " hand-" num)}
                     [:p rank]])))
              (let [rank (rank card)
                    color (name (:milo.card/color card))
                    classes (str "card "
                                 color
                                 " rank- " rank
                                 " hand-" num
                                 (when (= selected-card card) " selected-card"))]
                [:div
                 {:key num
                  :class classes
                  :on-click #(when turn?
                               (rf/dispatch [:card-change card]))}
                 [:p rank]]))))
        hand)
       [[:div.draw-pile
         {:key "draw-pile"
          :on-click #(rf/dispatch [:source-change :draw-pile])
          :class (str "draw-pile" (when (= source :draw-pile) " selected-card"))}
         [:p "D"]]]
       (map
        (fn [card]
          (let [color (:milo.card/color card)]
            (when-not (= source color)
              (let [rank (rank card)
                    classes ["card"
                             (name color)
                             (str "rank-" rank)
                             (str (name color) "-discard")
                             (when (= source color) "selected-card")]]
                [:div
                 {:key classes
                  :class (str/join " " classes)
                  :on-click #(when destination
                               (rf/dispatch [:source-change color]))}
                 [:p rank]]))))
        available-discards))]]))
