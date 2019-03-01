(ns rems.actions.accept-invitation
  (:require [re-frame.core :as rf]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text text-format]]
            [rems.util :refer [dispatch! post!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ token]]
   (status-modal/common-pending-handler! (text :t.actions/accept-invitation))
   {:db (assoc db ::token token)
    ::accept-invitation [(get-in db [:identity :user]) token]}))

(rf/reg-sub ::token (fn [db] (::token db "")))

(defn errors-to-content [errors]
  [:div (for [{:keys [type token]} errors]
          [:p
           (case type
             :t.actions.errors/invalid-token (text-format :t.actions.errors/invalid-token token)
             (text type))])])

(defn error-handler [response]
  (status-modal/set-error!
   (merge {:on-close #(dispatch! "#/catalogue")}
          (if (:error response)
            {:result {:error response}}
            {:error-content (errors-to-content (:errors response))}))))

(defn success-handler [response]
  (cond (:success response)
        (status-modal/set-success! {:content (text :t.actions/accept-invitation-success)
                                    :on-close #(dispatch! (str "#/application/" (:application-id response)))})

        (= :already-member (:type (first (:errors response))))
        (status-modal/set-success! {:content (text :t.actions/accept-invitation-already-member)
                                    :on-close #(dispatch! (str "#/application/" (:application-id (first (:errors response)))))})

        :else (error-handler response)))

(rf/reg-fx
 ::accept-invitation
 (fn [[user token]]
   (post! (str "/api/applications/accept-invitation?invitation-token=" token)
          {:handler success-handler
           :error-handler error-handler
           :headers {"x-rems-user-id" (:eppn user)}})))

(defn accept-invitation-page []
  (let [token @(rf/subscribe [::token])]
    [:div
     [:h2 (text :t.actions/accept-invitation)]]))
