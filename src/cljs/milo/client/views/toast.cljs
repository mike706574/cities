(ns milo.client.views.toast
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [milo.client.misc :refer [pretty]]
            [taoensso.timbre :as log]))

(defn toast []
  (let [{:keys [message action-event action-label color] :as toast}  @(rf/subscribe [:toast])]
    (if toast
      [:div#toast.mdl-js-snackbar.mdl-snackbar.mdl-snackbar--active
       {:aria-hidden "false"}
       [:div.mdl-snackbar__text (or message "No message.")]
       (when action-event
         [:button.mdl-snackbar__action
          {:type "button"
           :on-click #(rf/dispatch action-event)}
          (or action-label "Perform Action")])]
      [:div#toast.mdl-js-snackbar.mdl-snackbar
       {:class ""
        :aria-hidden "true"}
       [:div.mdl-snackbar__text ""]])))
