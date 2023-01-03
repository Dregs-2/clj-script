#! /usr/bin/env inlein
'{:dependencies [[org.clojure/clojure "1.11.1"]
                 [http-kit "2.6.0"]
                 [org.clojars.askonomm/ruuter "1.3.2"]
                 [cheshire/cheshire "5.11.0"]
                 [com.alibaba.nacos/nacos-client "2.1.1"]
                 [ch.qos.logback/logback-classic "1.2.11"]]}

(import (java.util Properties))
(import (com.alibaba.nacos.api PropertyKeyConst NacosFactory))
(import (com.alibaba.nacos.api.naming NamingService))

(require '[clojure.pprint :as pprint])
(require '[cheshire.core :as json])
(require '[org.httpkit.server :as http-server])
(require '[ruuter.core :as ruuter])
(def args-map (apply array-map (map #(try (load-string %) (catch Exception _ %)) *command-line-args*)))

(def nacos-host (:nacos-host args-map "127.0.0.1"))
(def nacos-port (:nacos-port args-map 8848))
(def service-name (:service-name args-map "default-name"))
(def service-group (:service-group args-map "default-group"))
(def host (:host args-map "127.0.0.1"))
(def port (:port args-map 52002))
(def conf (:conf args-map nil))


(defn nacos-register [^String nacos-host ^Integer nacos-port ^String service-name ^String group-name ^String host ^Integer port]
  (let [properties (doto (Properties.) (.put PropertyKeyConst/SERVER_ADDR (str nacos-host ":" nacos-port)))
        ^NamingService naming-service (NacosFactory/createNamingService properties)]
    (.registerInstance naming-service service-name group-name host port)))


(defn route
  [path methods status body headers]
  (assert path)
  (let [method-set #{:get :post :put :delete :head :options :patch}
        methods (if methods methods (vec method-set))
        methods (if (coll? methods) (vec methods) [methods])
        methods (vec (filter method-set methods))]
    (map #(identity {:path path :method % :response {:status status :body (if (string? body) body (json/encode body)) :headers headers}}) methods)
    )
  )

(defn routes []
  (let [result (for [route-conf (or (some-> conf slurp load-string) [["/" :get 200 "OK" {}]])]
                 (route (nth route-conf 0 nil) (nth route-conf 1 nil) (nth route-conf 2 200) (nth route-conf 3 nil) (nth route-conf 4 {})))]
    (apply concat result)
    ))

;(nacos-register nacos-host nacos-port service-name service-group host port)

(http-server/run-server #(ruuter/route (routes) %) {:port port})
