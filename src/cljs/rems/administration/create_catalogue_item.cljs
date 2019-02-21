(ns rems.administration.create-catalogue-item
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [text-field]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch post!]]
            [rems.status-modal :as status-modal]
            [reagent.core :as r]))

(defn- reset-form [db]
  (-> (dissoc db ::form)
      (assoc ::loading? true
             ::loading-progress 0)))

(defn- update-loading [db]
  (let [progress (::loading-progress db)]
    (if (<= 2 progress)
      (dissoc db ::loading-progress ::loading?)
      (assoc db ::loading-progress (inc progress)))))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (reset-form db)
    ::fetch-workflows nil
    ::fetch-resources nil
    ::fetch-forms nil}))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

; form state

(rf/reg-sub
 ::form
 (fn [db _]
   (::form db)))

(rf/reg-event-db
 ::set-form-field
 (fn [db [_ keys value]]
   (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub
 ::selected-workflow
 (fn [db _]
   (get-in db [::form :workflow])))

(rf/reg-event-db
 ::set-selected-workflow
 (fn [db [_ workflow]]
   (assoc-in db [::form :workflow] workflow)))

(rf/reg-sub
 ::selected-resource
 (fn [db _]
   (get-in db [::form :resource])))

(rf/reg-event-db
 ::set-selected-resource
 (fn [db [_ resource]]
   (assoc-in db [::form :resource] resource)))

(rf/reg-sub
 ::selected-form
 (fn [db _]
   (get-in db [::form :form])))

(rf/reg-event-db
 ::set-selected-form
 (fn [db [_ form]]
   (assoc-in db [::form :form] form)))

(defn- valid-request? [request]
  (and (not (str/blank? (:title request)))
       (number? (:wfid request))
       (number? (:resid request))
       (number? (:form request))))

(defn build-request [form]
  (let [request {:title (:title form)
                 :wfid (get-in form [:workflow :id])
                 :resid (get-in form [:resource :id])
                 :form (get-in form [:form :id])}]
    (when (valid-request? request)
      request)))

(defn- create-request-with-state [request]
  (if (nil? (:state request))
    (merge {:state "disabled"} request)
    request))

(defn- create-catalogue-item! [{:keys [request on-pending on-success on-error]}]
  (when on-pending (on-pending))
  (post! "/api/catalogue-items/create" {:params (create-request-with-state request)
                                        :handler on-success
                                        :error-handler on-error}))

(rf/reg-event-fx
 ::create-catalogue-item
 (fn [_ [_ opts]]
   (create-catalogue-item! opts)
   {}))


(defn- fetch-workflows []
  (fetch "/api/workflows/?active=true" {:handler #(rf/dispatch [::fetch-workflows-result %])}))

(rf/reg-fx
 ::fetch-workflows
 (fn [_]
   (fetch-workflows)))

(rf/reg-event-db
 ::fetch-workflows-result
 (fn [db [_ workflows]]
   (-> (assoc db ::workflows workflows)
       (update-loading))))

(rf/reg-sub
 ::workflows
 (fn [db _]
   (::workflows db)))

(defn- fetch-resources []
  (fetch "/api/resources/?active=true" {:handler #(rf/dispatch [::fetch-resources-result %])}))

(rf/reg-fx
 ::fetch-resources
 (fn [_]
   (fetch-resources)))

(rf/reg-event-db
 ::fetch-resources-result
 (fn [db [_ resources]]
   (-> (assoc db ::resources resources)
       (update-loading))))

(rf/reg-sub
 ::resources
 (fn [db _]
   (::resources db)))


(defn- fetch-forms []
  (fetch "/api/forms/?active=true" {:handler #(rf/dispatch [::fetch-forms-result %])}))

(rf/reg-fx
 ::fetch-forms
 (fn [_]
   (fetch-forms)))

(rf/reg-event-db
 ::fetch-forms-result
 (fn [db [_ forms]]
   (-> (assoc db ::forms forms)
       (update-loading))))

(rf/reg-sub
 ::forms
 (fn [db _]
   (::forms db)))


;;;; UI

(def ^:private context {:get-form ::form
                        :update-form ::set-form-field})

(defn- catalogue-item-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-catalogue-item/title)
                       :placeholder (text :t.create-catalogue-item/title-placeholder)}])

(defn- catalogue-item-workflow-field []
  (let [workflows @(rf/subscribe [::workflows])
        selected-workflow @(rf/subscribe [::selected-workflow])]
    [:div.form-group
     [:label (text :t.create-catalogue-item/workflow-selection)]
     [autocomplete/component
      {:value (when selected-workflow #{selected-workflow})
       :items workflows
       :value->text #(:title %2)
       :item->key :id
       :item->text :title
       :item->value identity
       :search-fields [:title]
       :add-fn #(rf/dispatch [::set-selected-workflow %])
       :remove-fn #(rf/dispatch [::set-selected-workflow nil])}]]))

(defn- catalogue-item-resource-field []
  (let [resources @(rf/subscribe [::resources])
        selected-resource @(rf/subscribe [::selected-resource])]
    [:div.form-group
     [:label (text :t.create-catalogue-item/resource-selection)]
     [autocomplete/component
      {:value (when selected-resource #{selected-resource})
       :items resources
       :value->text #(:resid %2)
       :item->key :id
       :item->text :resid
       :item->value identity
       :search-fields [:resid]
       :add-fn #(rf/dispatch [::set-selected-resource %])
       :remove-fn #(rf/dispatch [::set-selected-resource nil])}]]))

(defn- catalogue-item-form-field []
  (let [forms @(rf/subscribe [::forms])
        selected-form @(rf/subscribe [::selected-form])]
    [:div.form-group
     [:label (text :t.create-catalogue-item/form-selection)]
     [autocomplete/component
      {:value (when selected-form #{selected-form})
       :items forms
       :value->text #(:title %2)
       :item->key :id
       :item->text :title
       :item->value identity
       :search-fields [:title]
       :add-fn #(rf/dispatch [::set-selected-form %])
       :remove-fn #(rf/dispatch [::set-selected-form nil])}]]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/catalogue-items")}
   (text :t.administration/cancel)])

(defn- save-catalogue-item-button [on-click]
  (let [form @(rf/subscribe [::form])
        request (build-request form)]
    [:button.btn.btn-primary
     {:on-click #(on-click request)
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn create-catalogue-item-page []
  (let [loading? (rf/subscribe [::loading?])
        {:keys [on-pending on-success on-error state-atom] :as modal-opts}
        (status-modal/status-modal-opts
         {:on-close-after-success #(dispatch! "#/administration/catalogue-items")
          :description (text :t.administration/create-catalogue-item)})]
    (fn []
      [:div
      [administration-navigator-container]
      [:h2 (text :t.administration/create-catalogue-item)]
      [collapsible/component
       {:id "create-catalogue-item"
        :title (text :t.administration/create-catalogue-item)
        :always [:div
                 (if @loading?
                   [:div#catalogue-item-loader [spinner/big]]
                   [:div#catalogue-item-editor
                    [status-modal/situational-status-modal @state-atom modal-opts]
                    [catalogue-item-title-field]
                    [catalogue-item-workflow-field]
                    [catalogue-item-resource-field]
                    [catalogue-item-form-field]

                    [:div.col.commands
                     [cancel-button]
                     [save-catalogue-item-button #(rf/dispatch [::create-catalogue-item
                                                                {:request %
                                                                 :on-pending on-pending
                                                                 :on-success on-success
                                                                 :on-error on-error}])]]])]}]])))
