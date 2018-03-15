(ns status-im.ui.screens.profile.events
  (:require [clojure.spec.alpha :as spec]
            [re-frame.core :as re-frame]
            [status-im.ui.components.react :refer [show-image-picker]]
            [status-im.chat.constants :as chat-const]
            [status-im.ui.screens.profile.navigation]
            [status-im.ui.screens.accounts.events :as accounts-events]
            [status-im.chat.events :as chat-events]
            [status-im.chat.events.input :as input-events]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.image-processing :refer [img->base64]]
            [taoensso.timbre :as log]))

(re-frame/reg-fx
  :open-image-picker
  ;; the image picker is only used here for now, this effect can be use in other scenarios as well
  (fn [callback-event]
    (show-image-picker
     (fn [image]
       (let [path (get (js->clj image) "path")
             _ (log/debug path)
             on-success (fn [base64]
                          (re-frame/dispatch [callback-event base64]))
             on-error   (fn [type error]
                          (.log js/console type error))]
         (img->base64 path on-success on-error))))))

(handlers/register-handler-fx
  :profile/send-transaction
  [(re-frame/inject-cofx :get-stored-chat) re-frame/trim-v]
  (fn [{{:contacts/keys [contacts] :as db} :db :as cofx} [chat-id]]
    (let [send-command (get-in contacts chat-const/send-command-ref)
          fx (chat-events/start-chat chat-id true cofx)]
      (merge fx (input-events/select-chat-input-command (:db fx) send-command nil true)))))

(defn get-current-account [{:accounts/keys [current-account-id] :as db}]
  (get-in db [:accounts/accounts current-account-id]))

(defn valid-name? [name]
  (spec/valid? :profile/name name))

(handlers/register-handler-fx
  :my-profile/update-name
  (fn [{:keys [db]} [_ name]]
    {:db (-> db
             (assoc-in [:my-profile/profile :valid-name?] (valid-name? name))
             (assoc-in [:my-profile/profile :name] name))}))

(handlers/register-handler-fx
  :my-profile/update-picture
  (fn [{:keys [db]} [this-event base64-image]]
    (if base64-image
      {:db (assoc-in db [:my-profile/profile :photo-path] (str "data:image/jpeg;base64," base64-image))}
      {:open-image-picker this-event})))

(defn clean-name [{:accounts/keys [current-account-id] :as db} edit-view]
  (let [name (get-in db [edit-view :name])]
    (if (valid-name? name)
      name
      (get-in db [:accounts/accounts current-account-id :name]))))

(defn clear-profile [fx]
  (update fx :db dissoc :my-profile/profile :my-profile/default-name :my-profile/editing?))

(handlers/register-handler-fx
  :my-profile/start-editing-profile
  (fn [{:keys [db]} []]
    {:db (assoc db :my-profile/editing? true)}))

(handlers/register-handler-fx
  :my-profile/save-profile
  (fn [{:keys [db now]} _]
    (let [{:keys [photo-path]} (:my-profile/profile db)
          cleaned-name (clean-name db :my-profile/profile)
          cleaned-edit (merge {:name         cleaned-name
                               :last-updated now}
                              (if photo-path
                                {:photo-path photo-path}))]
      (-> (clear-profile {:db db})
          (accounts-events/account-update cleaned-edit)
          (update :dispatch-n concat [[:navigate-back]])))))

(handlers/register-handler-fx
  :group-chat-profile/start-editing
  (fn [{:keys [db]} _]
    {:db (assoc db :group-chat-profile/editing? true)}))

(handlers/register-handler-fx
  :group-chat-profile/save-profile
  (fn [{:keys [db]} _]
    (-> {:db db}
        (update :db dissoc :group-chat-profile/editing?))))

(handlers/register-handler-fx
  :my-profile/enter-two-random-words
  (fn [{:keys [db]} []]
    (let [{:keys [mnemonic]} (get-current-account db)
          shuffled-mnemonic (shuffle (map-indexed vector (clojure.string/split mnemonic #" ")))]
      {:db (assoc db :my-profile/seed {:step :first-word
                                       :first-word (first shuffled-mnemonic)
                                       :second-word (second shuffled-mnemonic)})})))

(handlers/register-handler-fx
  :my-profile/set-step
  (fn [{:keys [db]} [_ step]]
    {:db (update db :my-profile/seed assoc :step step :error nil :word nil)}))

(handlers/register-handler-fx
  :my-profile/finish
  (fn [{:keys [db]} _]
    (-> {:db (update db :my-profile/seed assoc :step :finish :error nil :word nil)}
        (accounts-events/account-update {:seed-backed-up? true}))))
