(ns cities.frontend.views.toast
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.frontend.misc :refer [pretty]]
            [taoensso.timbre :as log]))

(defn toast []
  (let [{:keys [message action-event action-label color] :as toast}  @(rf/subscribe [:toast])]
    (if toast
      [:div#toast.toaster.mdl-js-snackbar.mdl-snackbar.mdl-snackbar--active
       {:aria-hidden "false"
        :style {"zIndex" "10"}}
       [:div.mdl-snackbar__text (or message "No message.")]
       (when action-event
         [:button.mdl-snackbar__action
          {:type "button"
           :on-click #(rf/dispatch action-event)}
          (or action-label "Perform Action")])]
      [:div#toast.mdl-js-snackbar.mdl-snackbar
       {:class ""
        :style {"zIndex" "10"}
        :aria-hidden "true"}
       [:div.mdl-snackbar__text ""]])))
