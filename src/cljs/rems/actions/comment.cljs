(ns rems.actions.comment
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-form-view button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn open-form
  [{:keys [db]} _]
  (merge {:db (assoc db ::comment "")}))

(rf/reg-event-fx ::open-form open-form)

(rf/reg-sub ::comment (fn [db _] (::comment db)))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ value]] (assoc db ::comment value)))

(defn- send-comment! [{:keys [application-id comment on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/comment
                   :comment comment}
          :handler on-success ; TODO interpret :errors as failure
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-comment
 (fn [{:keys [db]} [_ {:keys [application-id comment on-pending on-success on-error]}]]
   (send-comment! {:application-id application-id
                   :comment comment
                   :on-success on-success
                   :on-error on-error})
   (on-pending)
   {}))

(defn comment-view
  [{:keys [comment on-set-comment on-send]}]
  [action-form-view "comment"
   (text :t.actions/comment)
   nil
   [[button-wrapper {:id "comment"
                    :text (text :t.actions/comment)
                    :on-click on-send}]]
   [:div [:div.form-group
          [:label {:for "comment"} (text :t.form/add-comments-not-shown-to-applicant)]
          [textarea {:id "comment"
                     :name "comment"
                     :placeholder (text :t.form/comment)
                     :value comment
                     :on-change #(on-set-comment (.. % -target -value))}]]]
   nil
   nil])

(defn comment-form [application-id on-finished]
  (let [comment (rf/subscribe [::comment])
        description (text :t.actions/comment)
        state (r/atom nil)
        on-pending #(reset! state {:status :pending})
        on-success #(reset! state {:status :saved })
        on-error #(reset! state {:status :failed :error %})
        on-modal-close #(do (reset! state nil)
                            (on-finished))]
    (fn [application-id]
      [:div
       (when (:status @state)
         [status-modal (assoc @state
                              :description (text :t.actions/comment)
                              :on-close on-modal-close)])
       [comment-view {:comment @comment
                      :on-set-comment #(rf/dispatch [::set-comment %])
                      :on-send #(rf/dispatch [::send-comment {:application-id application-id
                                                              :comment @comment
                                                              :on-pending on-pending
                                                              :on-success on-success
                                                              :on-error on-error}])}]])))