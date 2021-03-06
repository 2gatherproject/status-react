(ns status-im.wallet.collectibles.core
  (:require [re-frame.core :as re-frame]
            [status-im.browser.core :as browser]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.erc721 :as erc721]
            [status-im.ethereum.tokens :as tokens]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.money :as money]
            [status-im.utils.http :as http]
            [clojure.string :as string]
            [status-im.utils.types :as types]))

;;TODO: REPLACE ALL HANDLERS BY FX/DEFN

(defmulti load-collectible-fx (fn [_ symbol _] symbol))

(defmethod load-collectible-fx :default [_ _ _] nil)

(defmulti load-collectibles-fx (fn [_ symbol _ _ _] symbol))

(defmethod load-collectibles-fx :default [all-tokens symbol items-number address]
  {:load-collectibles-fx [all-tokens symbol items-number address]})

(defn load-token [i items-number contract address symbol]
  (when (< i items-number)
    (erc721/token-of-owner-by-index contract address i
                                    (fn [response]
                                      (load-token (inc i) items-number contract address symbol)
                                      (re-frame/dispatch [:load-collectible symbol response])))))

(re-frame/reg-fx
 :load-collectibles-fx
 (fn [[all-tokens symbol items-number address]]
   (let [contract (:address (tokens/symbol->token all-tokens symbol))]
     (load-token 0 items-number contract address symbol))))

(handlers/register-handler-fx
 :show-collectibles-list
 (fn [{:keys [db]} [_ {:keys [symbol amount] :as collectible} address]]
   (let [all-tokens          (:wallet/all-tokens db)
         items-number        (money/to-number amount)
         loaded-items-number (count (get-in db [:collectibles symbol]))]
     (merge (when (not= items-number loaded-items-number)
              (load-collectibles-fx all-tokens symbol items-number address))
            {:dispatch [:navigate-to :collectibles-list collectible]}))))

;; Crypto Kitties
(def ck :CK)

(handlers/register-handler-fx
 :load-kitties
 (fn [{db :db} [_ ids]]
   {:db         (update-in db [:collectibles] merge {ck (sorted-map-by >)})
    :http-get-n (mapv (fn [id]
                        {:url        (str "https://api.cryptokitties.co/kitties/" id)
                         :on-success (fn [o]
                                       (re-frame/dispatch [:load-collectible-success ck {id (http/parse-payload o)}]))
                         :on-error   (fn [o]
                                       (re-frame/dispatch [:load-collectible-failure ck {id (http/parse-payload o)}]))})
                      ids)}))

(defmethod load-collectibles-fx ck [_ _ items-number address _]
  {:http-get-n (mapv (fn [offset]
                       {:url        (str "https://api.cryptokitties.co/kitties?limit=20&offset="
                                         offset
                                         "&owner_wallet_address="
                                         address
                                         "&parents=false")
                        :on-success (fn [o]
                                      (re-frame/dispatch [:load-kitties (map :id (:kitties (http/parse-payload o)))]))
                        :on-error   (fn [o]
                                      (re-frame/dispatch [:load-collectibles-failure (http/parse-payload o)]))
                        :timeout-ms 10000})
                     (range 0 items-number 20))}) ;; Cryptokitties API limited to 20 items per request

;; Crypto Strikers
(def strikers :STRK)

(defmethod load-collectible-fx strikers [_ _ id]
  {:http-get {:url        (str "https://us-central1-cryptostrikers-prod.cloudfunctions.net/cards/" id)
              :on-success (fn [o]
                            (re-frame/dispatch [:load-collectible-success strikers {id (http/parse-payload o)}]))
              :on-error   (fn [o]
                            (re-frame/dispatch [:load-collectible-failure strikers {id (http/parse-payload o)}]))}})

;;Etheremona
(def emona :EMONA)

(defmethod load-collectible-fx emona [_ _ id]
  {:http-get {:url        (str "https://www.etheremon.com/api/monster/get_data?monster_ids=" id)
              :on-success (fn [o]
                            (re-frame/dispatch [:load-collectible-success emona (:data (http/parse-payload o))]))
              :on-error   (fn [o]
                            (re-frame/dispatch [:load-collectible-failure emona {id (http/parse-payload o)}]))}})

;;Kudos
(def kudos :KDO)

(defmethod load-collectible-fx kudos [{db :db} symbol id]
  {:erc721-token-uri [(:wallet/all-tokens db) symbol id]})

(re-frame/reg-fx
 :erc721-token-uri
 (fn [[all-tokens symbol tokenId]]
   (let [contract (:address (tokens/symbol->token all-tokens symbol))]
     (erc721/token-uri contract
                       tokenId
                       #(re-frame/dispatch [:token-uri-success
                                            tokenId
                                            (when %
                                              (subs % (.indexOf ^js % "http")))]))))) ;; extra chars in rinkeby

;;Superrare
(def superrare :SUPR)

(defmethod load-collectible-fx superrare [_ _ ids]
  {:http-get-n (mapv (fn [id]
                       {:url        id
                        :on-success (fn [o]
                                      (re-frame/dispatch [:load-collectible-success superrare {id (http/parse-payload o)}]))
                        :on-error   (fn [o]
                                      (re-frame/dispatch [:load-collectible-failure superrare {id (http/parse-payload o)}]))})
                     ids)})

(def graphql-url "https://api.pixura.io/graphql")

(defn graphql-query [address]
  (str "{
         collectiblesByOwner: allErc721Tokens(condition: {owner: \"" address "\"}) {
           collectibles: nodes {
            tokenId,
            metadata: erc721MetadatumByTokenId {
              metadataUri,
              description,
              name,
              imageUri
            }}}}"))

(defmethod load-collectibles-fx superrare [_ _ _ address _]
  {:http-post {:url        graphql-url
               :data       (types/clj->json {:query (graphql-query (ethereum/naked-address address))})
               :opts       {:headers {"Content-Type" "application/json"}}
               :on-success (fn [{:keys [response-body]}]
                             (re-frame/dispatch [:store-collectibles superrare
                                                 (get-in (http/parse-payload response-body)
                                                         [:data :collectiblesByOwner :collectibles])]))
               :on-error   (fn [{:keys [response-body]}]
                             (re-frame/dispatch [:load-collectibles-failure (http/parse-payload response-body)]))
               :timeout-ms 10000}})

(handlers/register-handler-fx
 :token-uri-success
 (fn [_ [_ tokenId token-uri]]
   {:http-get {:url
               token-uri
               :on-success
               (fn [o]
                 (re-frame/dispatch [:load-collectible-success kudos
                                     {tokenId (update (http/parse-payload o)
                                                      :image
                                                      string/replace
                                                      #"http:"
                                                      "https:")}])) ;; http in mainnet
               :on-error
               (fn [o]
                 (re-frame/dispatch [:load-collectible-failure kudos {tokenId (http/parse-payload o)}]))}}))

(handlers/register-handler-fx
 :load-collectible
 (fn [cofx [_ symbol token-id]]
   (load-collectible-fx cofx symbol token-id)))

(handlers/register-handler-fx
 :store-collectibles
 (fn [{db :db} [_ symbol collectibles]]
   {:db (update-in db [:collectibles symbol] merge
                   (reduce #(assoc %1 (:tokenId %2) %2) {} collectibles))}))

(handlers/register-handler-fx
 :load-collectible-success
 (fn [{db :db} [_ symbol collectibles]]
   {:db (update-in db [:collectibles symbol] merge collectibles)}))

(handlers/register-handler-fx
 :load-collectibles-failure
 (fn [{db :db} [_ reason]]
   {:db (update-in db [:collectibles symbol :errors] merge reason)}))

(handlers/register-handler-fx
 :load-collectible-failure
 (fn [{db :db} [_]]
   {:db db}))

(handlers/register-handler-fx
 :open-collectible-in-browser
 (fn [cofx [_ url]]
   (browser/open-url cofx url)))
