(ns rems.permissions
  (:require [clojure.test :refer [deftest is testing]]))

(def ^:private conj-set (fnil conj #{}))

(defn- give-role-to-user [application role user]
  (assert (keyword? role) {:role role})
  (assert (string? user) {:user user})
  (update-in application [::user-roles user] conj-set role))

(defn give-role-to-users [application role users]
  (reduce (fn [app user]
            (give-role-to-user app role user))
          application
          users))

(defn- dissoc-if-empty [m k]
  (if (empty? (get m k))
    (dissoc m k)
    m))

(defn remove-role-from-user [application role user]
  (assert (keyword? role) {:role role})
  (assert (string? user) {:user user})
  (-> application
      (update-in [::user-roles user] disj role)
      (update ::user-roles dissoc-if-empty user)))

(defn user-roles [application user]
  (set (get-in application [::user-roles user])))

(deftest test-user-roles
  (testing "give first role"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (give-role-to-user :role-1 "user")))))
  (testing "give more roles"
    (is (= {::user-roles {"user" #{:role-1 :role-2}}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")))))
  (testing "remove some roles"
    (is (= {::user-roles {"user" #{:role-1}}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")
               (remove-role-from-user :role-2 "user")))))
  (testing "remove all roles"
    (is (= {::user-roles {}}
           (-> {}
               (give-role-to-user :role-1 "user")
               (remove-role-from-user :role-1 "user")))))
  (testing "give a role to multiple users"
    (is (= {::user-roles {"user-1" #{:role-1}
                          "user-2" #{:role-1}}}
           (-> {}
               (give-role-to-users :role-1 ["user-1" "user-2"])))))
  (testing "multiple users, get the roles of a single user"
    (let [app (-> {}
                  (give-role-to-user :role-1 "user-1")
                  (give-role-to-user :role-2 "user-2"))]
      (is (= #{:role-1} (user-roles app "user-1")))
      (is (= #{:role-2} (user-roles app "user-2")))
      (is (= #{} (user-roles app "user-3"))))))

(defn has-any-role? [application user]
  (seq (user-roles application user)))

(deftest test-has-any-role?
  (testing "no roles"
    (is (not (-> {}
                 (has-any-role? "user")))))
  (testing "some roles"
    (is (-> {}
            (give-role-to-user :role "user")
            (has-any-role? "user")))))

(defn set-role-permissions
  "Sets role specific permissions for the application.

   In `permission-map`, the key is the role (a keyword), and the value
   is a list of permissions to set for that role (also keywords).
   The permissions may represent commands that the user is allowed to run,
   or they may be used to specify whether the user can see all events and
   comments from the reviewers (e.g. `:see-everything`)."
  [application permission-map]
  (reduce (fn [application [role permissions]]
            (assert (keyword? role) {:role role})
            (assoc-in application [::role-permissions role] (set permissions)))
          application
          permission-map))

(deftest test-set-role-permissions
  (testing "adding"
    (is (= {::role-permissions {:role #{:foo :bar}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})))))
  (testing "updating"
    (is (= {::role-permissions {:role #{:gazonk}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (set-role-permissions {:role [:gazonk]})))))
  (testing "removing"
    (is (= {::role-permissions {:role #{}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (set-role-permissions {:role []}))))
    (is (= {::role-permissions {:role #{}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (set-role-permissions {:role nil})))))

  (testing "can set permissions for multiple roles"
    (is (= {::role-permissions {:role-1 #{:foo}
                                :role-2 #{:bar}}}
           (-> {}
               (set-role-permissions {:role-1 [:foo]
                                      :role-2 [:bar]})))))
  (testing "does not alter unrelated roles"
    (is (= {::role-permissions {:unrelated #{:foo}
                                :role #{:gazonk}}}
           (-> {}
               (set-role-permissions {:unrelated [:foo]
                                      :role [:bar]})
               (set-role-permissions {:role [:gazonk]}))))))

(defn remove-permission-from-all [application permission]
  (let [roles (keys (::role-permissions application))]
    (reduce (fn [application role]
              (update-in application [::role-permissions role] disj permission))
            application
            roles)))

(deftest test-remove-permission-from-all
  (testing "removes the permission from all roles"
    (is (= {::role-permissions {:role-1 #{}
                                :role-2 #{}}}
           (-> {}
               (set-role-permissions {:role-1 [:foo]
                                      :role-2 [:foo]})
               (remove-permission-from-all :foo)))))
  (testing "leaves unrelated permissions unchanged"
    (is (= {::role-permissions {:role #{:bar}}}
           (-> {}
               (set-role-permissions {:role [:foo :bar]})
               (remove-permission-from-all :foo))))))

(defn user-permissions
  "Returns the specified user's permissions to this application.
   Union of all role specific permissions."
  [application user]
  (->> (user-roles application user)
       (mapcat (fn [role]
                 (get-in application [::role-permissions role])))
       set))

(deftest test-user-permissions
  (testing "unknown user"
    (is (= #{}
           (user-permissions {} "user"))))
  (testing "one role"
    (is (= #{:foo}
           (-> {}
               (give-role-to-user :role-1 "user")
               (set-role-permissions {:role-1 #{:foo}})
               (user-permissions "user")))))
  (testing "multiple roles"
    (is (= #{:foo :bar}
           (-> {}
               (give-role-to-user :role-1 "user")
               (give-role-to-user :role-2 "user")
               (set-role-permissions {:role-1 #{:foo}
                                      :role-2 #{:bar}})
               (user-permissions "user"))))))

(defn cleanup [application]
  (dissoc application ::user-roles ::role-permissions))
