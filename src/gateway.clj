#! /usr/bin/env inlein
'{:dependencies [[org.clojure/clojure "1.11.1"] [io.vertx/vertx-http-proxy "4.3.4"]]}

(import (io.vertx.core Future Handler Vertx))
(import (io.vertx.core.http HttpClient HttpServer HttpServerRequest HttpHeaders))
(import (io.vertx.core.net SocketAddress))
(import (io.vertx.httpproxy HttpProxy ProxyContext ProxyInterceptor ProxyRequest))
(import (java.util.function Function))
(require '[clojure.string :as str])

(def args-map (apply array-map (map #(try (load-string %) (catch Exception _ %)) *command-line-args*)))
(def conf (:conf args-map nil))
(def port (:port args-map 52003))

(def service-proxy-address #(get (or (some->> conf slurp load-string) {}) %))

(defn service-name [^String uri]
  (let [uri (if (str/starts-with? uri "/") uri (str "/" uri))
        uris (str/split uri #"/")]
    (if (<= 1 (count uris)) (second uris) nil)
    )
  )

(defn real-uri [^String uri]
  (let [uri (if (str/starts-with? uri "/") uri (str "/" uri))
        uris (str/split uri #"/")
        suffix (if (str/ends-with? uri "/") "/" "")]
    (->> uris rest rest (str/join "/") (str "/") (#(str % suffix)))
    )
  )

(defn ^SocketAddress service-address [^String uri]
  (let [service-name (service-name uri) address (service-proxy-address service-name)]
    (let [[host port] address]
      (when host (SocketAddress/inetSocketAddress (if port port 80) host))
      )
    )
  )

(defn ^Function origin-selector []
  (proxy [Function] []
    (apply [^HttpServerRequest request]
      (let [address (service-address (.uri request))]
        (Future/succeededFuture address)
        )
      ))
  )
(defn proxy-interceptor []
  (proxy [ProxyInterceptor] []
    (handleProxyRequest [^ProxyContext context]
      (let [^ProxyRequest request (.request context)]
        (.setURI request (real-uri (.getURI request)))
        (authenticate request)
        )
      (.sendRequest context)
      )
    (handleProxyResponse [^ProxyContext context]
      (.sendResponse context)
      )
    )
  )

(defn http-server-handler [http-proxy]
  (proxy [Handler] []
    (handle [^HttpServerRequest request]
      (let [uri (.uri request) address (service-address uri) response (.response request)]
        (if address
          (do (.handle http-proxy request)
              (println (format "%s [%s%s -> %s%s]"
                               (.format java.time.format.DateTimeFormatter/ISO_LOCAL_DATE_TIME (java.time.LocalDateTime/now))
                               (.host request) (.uri request) (str address) (real-uri uri))))
          (do (.setStatusCode response 404)
              (.end response)))
        )
      )
    )
  )

(let [^Vertx vertx (Vertx/vertx)
      ^HttpClient http-client (.createHttpClient vertx)
      ^HttpProxy http-proxy (HttpProxy/reverseProxy http-client)
      ^HttpServer http-server (.createHttpServer vertx)
      ^Handler http-handler (http-server-handler http-proxy)]
  (.originSelector http-proxy (origin-selector))
  (.addInterceptor http-proxy (proxy-interceptor))
  (.requestHandler http-server http-handler)
  (let [^Future future (.listen http-server port)]
    (.onSuccess future (reify Handler (handle [this _] (println (format "gateway listen on port %s" port)))))
    (.onFailure future (reify Handler (handle [this _] (throw _))))
    )
  )