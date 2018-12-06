(ns status-im.react-native.js-dependencies)

(def config                 (js/require "react-native-config"))
(def fs                     (js/require "react-native-fs"))
(def http-bridge            (js/require "react-native-http-bridge"))
(def keychain               (js/require "react-native-keychain"))
(def qr-code                (js/require "react-native-qrcode"))
(def react-native           (js/require "react-native"))
(def status-keycard         (js/require "react-native-status-keycard"))
(def realm                  (js/require "realm"))
(def webview-bridge         (js/require "react-native-webview-bridge"))
(def secure-random          (.-generateSecureRandom (js/require "react-native-securerandom")))
(def EventEmmiter           (js/require "react-native/Libraries/vendor/emitter/EventEmitter"))
(def fetch                  (.-default (js/require "react-native-fetch-polyfill")))
(def i18n                   (.-default (js/require "react-native-i18n")))
(def camera                 (js/require "react-native-camera"))
(def dialogs                (js/require "react-native-dialogs"))
(def dismiss-keyboard       (js/require "dismissKeyboard"))
(def image-crop-picker      (js/require "react-native-image-crop-picker"))
(def image-resizer          (js/require "react-native-image-resizer"))
(def svg                    (js/require "react-native-svg"))
(def react-native-firebase  (js/require "react-native-firebase"))
(def snoopy                 (js/require "rn-snoopy"))
(def snoopy-filter          (js/require "rn-snoopy/stream/filter"))
(def snoopy-bars            (js/require "rn-snoopy/stream/bars"))
(def snoopy-buffer          (js/require "rn-snoopy/stream/buffer"))
(def background-timer       (.-default (js/require "react-native-background-timer")))
(def react-navigation       (js/require "react-navigation"))
(def desktop-linking        #js {:addEventListener (fn [])})
(def desktop-menu           #js {:addEventListener (fn [])})
(def desktop-config         #js {:addEventListener (fn [])})
(def desktop-shortcuts      #js {:addEventListener (fn [])})
