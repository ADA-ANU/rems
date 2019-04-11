(ns rems.administration.test-create-form
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [rems.administration.create-form :as f :refer [build-request build-localized-string]]
            [rems.testing :refer [isolate-re-frame-state stub-re-frame-effect]]
            [rems.util :refer [getx-in]]))

(use-fixtures :each isolate-re-frame-state)

(defn reset-form []
  (rf/dispatch-sync [::f/enter-page]))

(deftest add-form-field-test
  (let [form (rf/subscribe [::f/form])]
    (testing "adds fields"
      (reset-form)
      (is (= {:fields []}
             @form)
          "before")

      (rf/dispatch-sync [::f/add-form-field])

      (is (= {:fields [{:type "text"}]}
             @form)
          "after"))

    (testing "adds fields to the end"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "old field"])
      (is (= {:fields [{:type "text" :foo "old field"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/add-form-field])

      (is (= {:fields [{:type "text" :foo "old field"} {:type "text"}]}
             @form)
          "after"))))

(deftest remove-form-field-test
  (let [form (rf/subscribe [::f/form])]
    (testing "removes fields"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (is (= {:fields [{:type "text"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/remove-form-field 0])

      (is (= {:fields []}
             @form)
          "after"))

    (testing "removes only the field at the specified index"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "field 0"])
      (rf/dispatch-sync [::f/set-form-field [:fields 1 :foo] "field 1"])
      (rf/dispatch-sync [::f/set-form-field [:fields 2 :foo] "field 2"])
      (is (= {:fields [{:type "text" :foo "field 0"}
                      {:type "text" :foo "field 1"}
                      {:type "text" :foo "field 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/remove-form-field 1])

      (is (= {:fields [{:type "text" :foo "field 0"}
                      {:type "text" :foo "field 2"}]}
             @form)
          "after"))))

(deftest move-form-field-up-test
  (let [form (rf/subscribe [::f/form])]
    (testing "moves fields up"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "field 0"])
      (rf/dispatch-sync [::f/set-form-field [:fields 1 :foo] "field 1"])
      (rf/dispatch-sync [::f/set-form-field [:fields 2 :foo] "field X"])
      (is (= {:fields [{:type "text" :foo "field 0"}
                      {:type "text" :foo "field 1"}
                      {:type "text" :foo "field X"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/move-form-field-up 2])

      (is (= {:fields [{:type "text" :foo "field 0"}
                      {:type "text" :foo "field X"}
                      {:type "text" :foo "field 1"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [::f/move-form-field-up 1])

      (is (= {:fields [{:type "text" :foo "field X"}
                      {:type "text" :foo "field 0"}
                      {:type "text" :foo "field 1"}]}
             @form)
          "after move 2")

      (testing "unless already first"
        (rf/dispatch-sync [::f/move-form-field-up 0])

        (is (= {:fields [{:type "text" :foo "field X"}
                        {:type "text" :foo "field 0"}
                        {:type "text" :foo "field 1"}]}
               @form)
            "after move 3")))))

(deftest move-form-field-down-test
  (let [form (rf/subscribe [::f/form])]
    (testing "moves fields down"
      (reset-form)
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/add-form-field])
      (rf/dispatch-sync [::f/set-form-field [:fields 0 :foo] "field X"])
      (rf/dispatch-sync [::f/set-form-field [:fields 1 :foo] "field 1"])
      (rf/dispatch-sync [::f/set-form-field [:fields 2 :foo] "field 2"])
      (is (= {:fields [{:type "text" :foo "field X"}
                      {:type "text" :foo "field 1"}
                      {:type "text" :foo "field 2"}]}
             @form)
          "before")

      (rf/dispatch-sync [::f/move-form-field-down 0])

      (is (= {:fields [{:type "text" :foo "field 1"}
                      {:type "text" :foo "field X"}
                      {:type "text" :foo "field 2"}]}
             @form)
          "after move 1")

      (rf/dispatch-sync [::f/move-form-field-down 1])

      (is (= {:fields [{:type "text" :foo "field 1"}
                      {:type "text" :foo "field 2"}
                      {:type "text" :foo "field X"}]}
             @form)
          "after move 2")

      (testing "unless already last"
        (rf/dispatch-sync [::f/move-form-field-down 2])

        (is (= {:fields [{:type "text" :foo "field 1"}
                        {:type "text" :foo "field 2"}
                        {:type "text" :foo "field X"}]}
               @form)
            "after move 3")))))

(deftest build-request-test
  (let [form {:organization "abc"
              :title "the title"
              :fields [{:title {:en "en title"
                               :fi "fi title"}
                       :optional true
                       :type "text"
                       :maxlength "12"
                       :input-prompt {:en "en prompt"
                                      :fi "fi prompt"}}]}
        languages [:en :fi]]
    (testing "valid form"
      (is (= {:organization "abc"
              :title "the title"
              :fields [{:title {:en "en title"
                               :fi "fi title"}
                       :optional true
                       :type "text"
                       :maxlength 12
                       :input-prompt {:en "en prompt"
                                      :fi "fi prompt"}}]}
             (build-request form languages))))

    (testing "missing organization"
      (is (nil? (build-request (assoc-in form [:organization] "") languages))))

    (testing "missing title"
      (is (nil? (build-request (assoc-in form [:title] "") languages))))

    (testing "zero fields is ok"
      (is (= {:organization "abc"
              :title "the title"
              :fields []}
             (build-request (assoc-in form [:fields] []) languages))))

    (testing "missing field title"
      (is (= nil
             (build-request (assoc-in form [:fields 0 :title :en] "") languages)
             (build-request (update-in form [:fields 0 :title] dissoc :en) languages)
             (build-request (assoc-in form [:fields 0 :title] nil) languages))))

    (testing "missing optional implies false"
      (is (false? (getx-in (build-request (assoc-in form [:fields 0 :optional] nil) languages)
                           [:fields 0 :optional]))))

    (testing "missing field type"
      (is (nil? (build-request (assoc-in form [:fields 0 :type] nil) languages))))

    (testing "input prompt is optional"
      (is (= {:en "" :fi ""}
             (getx-in (build-request (assoc-in form [:fields 0 :input-prompt] nil) languages)
                      [:fields 0 :input-prompt])
             (getx-in (build-request (assoc-in form [:fields 0 :input-prompt] {:en ""}) languages)
                      [:fields 0 :input-prompt])
             (getx-in (build-request (assoc-in form [:fields 0 :input-prompt] {:en "" :fi ""}) languages)
                      [:fields 0 :input-prompt]))))

    (testing "maxlength is optional"
      (is (nil? (getx-in (build-request (assoc-in form [:fields 0 :maxlength] "") languages)
                         [:fields 0 :maxlength])))
      (is (nil? (getx-in (build-request (assoc-in form [:fields 0 :maxlength] nil) languages)
                         [:fields 0 :maxlength]))))

    (testing "if you use input prompt, you must fill in all the languages"
      (is (= nil
             (build-request (assoc-in form [:fields 0 :input-prompt] {:en "en prompt" :fi ""}) languages)
             (build-request (assoc-in form [:fields 0 :input-prompt] {:en "en prompt"}) languages))))

    (testing "date fields"
      (let [form (assoc-in form [:fields 0 :type] "date")]

        (testing "valid form"
          (is (= {:organization "abc"
                  :title "the title"
                  :fields [{:title {:en "en title"
                                   :fi "fi title"}
                           :optional true
                           :type "date"}]}
                 (build-request form languages))))))

    (testing "option fields"
      (let [form (-> form
                     (assoc-in [:fields 0 :type] "option")
                     (assoc-in [:fields 0 :options] [{:key "yes"
                                                     :label {:en "en yes"
                                                             :fi "fi yes"}}
                                                    {:key "no"
                                                     :label {:en "en no"
                                                             :fi "fi no"}}]))]

        (testing "valid form"
          (is (= {:organization "abc"
                  :title "the title"
                  :fields [{:title {:en "en title"
                                   :fi "fi title"}
                           :optional true
                           :type "option"
                           :options [{:key "yes"
                                      :label {:en "en yes"
                                              :fi "fi yes"}}
                                     {:key "no"
                                      :label {:en "en no"
                                              :fi "fi no"}}]}]}
                 (build-request form languages))))

        (testing "missing option key"
          (is (= nil
                 (build-request (assoc-in form [:fields 0 :options 0 :key] "") languages)
                 (build-request (assoc-in form [:fields 0 :options 0 :key] nil) languages))))

        (testing "missing option label"
          (is (= nil
                 (build-request (assoc-in form [:fields 0 :options 0 :label] {:en "" :fi ""}) languages)
                 (build-request (assoc-in form [:fields 0 :options 0 :label] nil) languages))))))

    (testing "multiselect fields"
      (let [form (-> form
                     (assoc-in [:fields 0 :type] "multiselect")
                     (assoc-in [:fields 0 :options] [{:key "egg"
                                                     :label {:en "Egg"
                                                             :fi "Munaa"}}
                                                    {:key "bacon"
                                                     :label {:en "Bacon"
                                                             :fi "Pekonia"}}]))]

        (testing "valid form"
          (is (= {:organization "abc"
                  :title "the title"
                  :fields [{:title {:en "en title"
                                   :fi "fi title"}
                           :optional true
                           :type "multiselect"
                           :options [{:key "egg"
                                      :label {:en "Egg"
                                              :fi "Munaa"}}
                                     {:key "bacon"
                                      :label {:en "Bacon"
                                              :fi "Pekonia"}}]}]}
                 (build-request form languages))))

        (testing "missing option key"
          (is (= nil
                 (build-request (assoc-in form [:fields 0 :options 0 :key] "") languages)
                 (build-request (assoc-in form [:fields 0 :options 0 :key] nil) languages))))

        (testing "missing option label"
          (is (= nil
                 (build-request (assoc-in form [:fields 0 :options 0 :label] {:en "" :fi ""}) languages)
                 (build-request (assoc-in form [:fields 0 :options 0 :label] nil) languages))))))))

(deftest build-localized-string-test
  (let [languages [:en :fi]]
    (testing "localizations are copied as-is"
      (is (= {:en "x", :fi "y"}
             (build-localized-string {:en "x", :fi "y"} languages))))
    (testing "missing localizations default to empty string"
      (is (= {:en "", :fi ""}
             (build-localized-string {} languages))))
    (testing "additional languages are excluded"
      (is (= {:en "x", :fi "y"}
             (build-localized-string {:en "x", :fi "y", :sv "z"} languages))))))
