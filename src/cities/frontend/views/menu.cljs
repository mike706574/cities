(ns cities.frontend.views.menu
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.frontend.misc :refer [pretty]]
            [taoensso.timbre :as log]))

(defn button
  [label on-click]
  [:button
   {:value label
    :on-click  on-click}
   label])

(defn received-invites []
  (let [invites @(rf/subscribe [:received-invites])]
    (letfn [(list-item [index invite]
              [:li {:key index}
               [:span
                (str (first invite) " invited you to play.")]
               [:span
                (button "Accept" #(rf/dispatch [:accept-invite (first invite)]))]
               [:span
                (button "Reject" #(rf/dispatch [:reject-invite (first invite)]))]])]
      [:div
       [:h5.no-margin "Received Invites"]
       (if (empty? invites)
         [:p.spaced-text "You haven't received any invites."]
         [:ul
          (map-indexed list-item invites)])])))

(defn game-list
  [games]
  (letfn [(game-item [[id game]]
            (let [{:keys [::game/opponent]} game]
              [:li
               {:key id}
               [:span
                [:img
                 {:src (str "images/" @(rf/subscribe [:avatar opponent])) }]
                (str opponent " [" id "]")]
               [:span
                (button "Play" #(rf/dispatch [:play-game id]))]]))]
    [:ul
     (doall (map game-item games))]))

(defn ready-games []
  (let [games @(rf/subscribe [:ready-games])]
    [:div
     [:h5.no-margin "Your turn"]
     (if (empty? games)
       [:p.spaced-text "No games found."]
       (game-list games))]))

(defn waiting-games []
  (let [games @(rf/subscribe [:waiting-games])]
    [:div
     [:h5.no-margin"Their turn"]
     (if (empty? games)
       [:p.spaced-text"No games found."]
       (game-list games))]))

(defn sent-invites []
  (let [invites @(rf/subscribe [:sent-invites])]
    (letfn [(list-item [index invite]
              [:li
               [:span
                (str "You invited " (second invite) " to play.")]
               [:span
                (button "Cancel" #(rf/dispatch [:cancel-invite (second invite)]))]])]
      (if (empty? invites)
        [:p.spaced-text "You haven't sent any invites."]
        [:ul
         (map-indexed list-item invites)]))))

(defn invite-form []
  (let [recipient-model (r/atom "")
        res "^([a-zA-Z0-9_-])+$"
        re (re-pattern res)]
    (fn []
      (let [player @(rf/subscribe [:player])
            recipient @recipient-model
            valid? (and (re-find re recipient)
                        (not= player recipient))
            send-invite #(when valid?
                           (reset! recipient-model "")
                           (rf/dispatch [:send-invite recipient]))]
        [:div
         [:label "Player"]
         [:input {:type "text"
                  :on-change #(reset! recipient-model (-> % .-target .-value))
                  :value recipient
                  :max-length 33
                  :on-key-press #(when (= (.-charCode %) 13)
                                   (send-invite))}]
         [:button
          {:on-click #(reset! recipient-model "")
           :style {"marginLeft" "1em"}}
          "Clear"]
         [:button
          {:on-click send-invite
           :style {"marginLeft" "1em"}}
          "Send Invite"]]))))

(defn outbox
  []
  [:div
   [:h5.no-margin "Sent invites"]
   [invite-form]
   [sent-invites]])

(defn menu []
  (log/debug "Rendering menu.")
  [:div
   [ready-games]
   [waiting-games]
   [received-invites]
   [outbox]])
