(ns rems.actions.request-decision
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.atoms :refer [enrich-user]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(rf/reg-fx
 ::fetch-potential-deciders
 (fn [[user on-success]]
   (fetch "/api/applications/deciders"
          {:handler on-success
           :headers {"x-rems-user-id" (:eppn user)}})))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::potential-deciders #{}
               ::selected-deciders #{})
    ::fetch-potential-deciders [(get-in db [:identity :user])
                                #(rf/dispatch [::set-potential-deciders %])]}))

(rf/reg-sub ::potential-deciders (fn [db _] (::potential-deciders db)))
(rf/reg-event-db
 ::set-potential-deciders
 (fn [db [_ deciders]]
   (assoc db
          ::potential-deciders (set (map enrich-user deciders))
          ::selected-deciders #{})))

(rf/reg-sub ::selected-deciders (fn [db _] (::selected-deciders db)))
(rf/reg-event-db ::set-selected-deciders (fn [db [_ deciders]] (assoc db ::selected-deciders deciders)))
(rf/reg-event-db ::add-selected-decider (fn [db [_ decider]] (update db ::selected-deciders conj decider)))
(rf/reg-event-db ::remove-selected-decider (fn [db [_ decider]] (update db ::selected-deciders disj decider)))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-event-fx
 ::send-request-decision
 (fn [_ [_ {:keys [deciders application-id comment on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/request-decision))
   (post! "/api/applications/request-decision"
          {:params {:application-id application-id
                    :comment comment
                    :deciders (map :userid deciders)}
           :handler (partial status-modal/common-success-handler! on-finished)
           :error-handler status-modal/common-error-handler!})
   {}))

(def ^:private action-form-id "request-decision")

(defn request-decision-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/request-decision)
                  :on-click #(rf/dispatch [::open-form])}])

(defn request-decision-view
  [{:keys [selected-deciders potential-deciders comment on-set-comment on-add-decider on-remove-decider on-send]}]
  [action-form-view action-form-id
   (text :t.actions/request-decision)
   [[button-wrapper {:id "request-decision"
                     :text (text :t.actions/request-decision)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-not-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [:div.form-group
     [:label (text :t.actions/request-selection)]
     [autocomplete/component
      {:value (sort-by :display selected-deciders)
       :items potential-deciders
       :value->text #(:display %2)
       :item->key :userid
       :item->text :display
       :item->value identity
       :search-fields [:name :email]
       :add-fn on-add-decider
       :remove-fn on-remove-decider}]]]])

(defn request-decision-form [application-id on-finished]
  (let [selected-deciders @(rf/subscribe [::selected-deciders])
        potential-deciders @(rf/subscribe [::potential-deciders])
        comment @(rf/subscribe [::comment])]
    [request-decision-view {:selected-deciders selected-deciders
                            :potential-deciders potential-deciders
                            :comment comment
                            :on-set-comment #(rf/dispatch [::set-comment %])
                            :on-add-decider #(rf/dispatch [::add-selected-decider %])
                            :on-remove-decider #(rf/dispatch [::remove-selected-decider %])
                            :on-send #(rf/dispatch [::send-request-decision {:application-id application-id
                                                                             :deciders selected-deciders
                                                                             :comment comment
                                                                             :on-finished on-finished}])}]))
