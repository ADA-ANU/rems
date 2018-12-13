(ns rems.actions.action
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [textarea]]
            [rems.text :refer [text]]))

(defn- action-collapse-id [action-id]
  (str "actions-" action-id))

(defn button-wrapper [{:keys [id text class on-click]}]
  [:button.btn
   {:id id
    :class (or class :btn-secondary)
    :on-click on-click}
   text])

(defn cancel-action-button [id]
  [:button.btn.btn-secondary
   {:id (str "cancel-" id)
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))}
   (text :t.actions/cancel)])

(defn action-comment [{:keys [id label comment on-comment]}]
  (let [id (str "comment-" id)]
    [:div.form-group
     [:label {:for id} label]
     [textarea {:id id
                :name id
                :placeholder (text :t.actions/comment-placeholder)
                :value comment
                :on-change #(on-comment (.. % -target -value))}]]))

(defn action-form-view [id title buttons content]
  [:div.collapse {:id (action-collapse-id id) :data-parent "#actions-forms"}
   [:h4.mt-5 title]
   content
   (into [:div.col.commands.mr-3 [cancel-action-button id]] buttons)])

(defn action-button [{:keys [id text class on-click]}]
  [:button.btn.mr-3
   {:id (str id "-action-button")
    :class (or class "btn-secondary")
    :type "button"
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))
    :on-click on-click}
   (str text " ...")])
