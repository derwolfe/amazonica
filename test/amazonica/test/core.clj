(ns amazonica.test.core
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [amazonica.core :as c]
            [amazonica.aws
             autoscaling
             cloudformation
             cloudfront
             cloudsearchv2
             cloudsearchdomain
             cloudtrail
             cloudwatch
             codedeploy
             cognitoidentity
             cognitosync
             datapipeline
             directconnect
             dynamodbv2
             ec2
             ecs
             elasticache
             elasticbeanstalk
             elasticloadbalancing
             elasticmapreduce
             elastictranscoder
             glacier
             identitymanagement
             iot
             iotdata
             kinesis
             kms
             lambda
             logs
             machinelearning
             opsworks
             rds
             redshift
             route53
             s3
             securitytoken
             simpledb
             simpleemail
             simpleworkflow
             sns
             sqs
             storagegateway
             ]))

(deftest camel->keyword-tests
  (let [c->k #'c/camel->keyword]
    (is (= :abc-def (c->k "abcDef")))
    (is (= :abc-def (c->k "AbcDef")))

    (is (= :abcdef (c->k "ABCDef")))

    (is (= :describe-storedi-scsivolumes (c->k "DescribeStorediSCSIVolumes")))
    (is (= :list-open-idconnect-providers (c->k "ListOpenIDConnectProviders")))))

(deftest camel->keyword2-tests
  (let [c->k #'c/camel->keyword2]
    (is (= :abc-def (c->k "abcDef")))
    (is (= :abc-def (c->k "AbcDef")))

    (is (= :abc-def (c->k "ABCDef")))

    (is (= :describe-stored-iscsi-volumes (c->k "DescribeStorediSCSIVolumes")))
    (is (= :list-openid-connect-providers (c->k "ListOpenIDConnectProviders")))))

(def changed-camel->keyword-vars
  "These are vars that were converted to kebab-case in a way that didn't deal
  with acronyms (any sequence of capital letters, but also some things like
  iSCSI and OpenID) correctly. This exists so that we can verify that we didn't
  accidentally break backwards compatibility.

  This is a collection of strings and symbols and not just vars so we can verify
  other information about the two resulting vars; and that you get a nice test
  failure instead of an ugly compilation error when it fails."
  {"identitymanagement" [['list-mfadevices
                          'list-mfa-devices]
                         ['list-open-idconnect-providers
                          'list-openid-connect-providers]
                         ['list-samlproviders
                          'list-saml-providers]
                         ['list-sshpublic-keys
                          'list-ssh-public-keys]
                         ['list-virtual-mfadevices
                          'list-virtual-mfa-devices]]
   "iot" [['describe-cacertificate
           'describe-ca-certificate]
          ['list-cacertificates
           'list-ca-certificates]]
   "rds" [['describe-dbcluster-parameter-groups
           'describe-db-cluster-parameter-groups]
          ['describe-dbcluster-snapshots
           'describe-db-cluster-snapshots]
          ['describe-dbclusters
           'describe-db-clusters]
          ['describe-dbengine-versions
           'describe-db-engine-versions]
          ['describe-dbinstances
           'describe-db-instances]
          ['describe-dbparameter-groups
           'describe-db-parameter-groups]
          ['describe-dbsecurity-groups
           'describe-db-security-groups]
          ['describe-dbsnapshots
           'describe-db-snapshots]
          ['describe-dbsubnet-groups
           'describe-db-subnet-groups]]
   "storagegateway" [['describe-storedi-scsivolumes
                      'describe-stored-iscsi-volumes]
                     ['describe-cachedi-scsivolumes
                      'describe-cached-iscsi-volumes]
                     ['describe-nfsfile-shares
                      'describe-nfs-file-shares]]
   "machinelearning" [['describe-mlmodels
                       'describe-ml-models]]})

(deftest camel->keyword-changes-tests
  "See https://github.com/mcohen01/amazonica/issues/256"
  (doseq [[service changed-fn-names] changed-camel->keyword-vars
          [old-name new-name] changed-fn-names
          :let [service-ns-sym (symbol (str "amazonica.aws." service))
                old (ns-resolve service-ns-sym old-name)
                new (ns-resolve service-ns-sym new-name)]]
    (is (not= old-name new-name))
    (is (some? old) (str "missing old var: " service " " old-name))
    (is (some? new) (str "missing new var: " service " " new-name))

    (is (= #{:ns
             :name
             :amazonica/client
             :amazonica/methods
             :amazonica/deprecated-in-favor-of}
           (set (keys (meta old)))))
    (is (= #{:ns :name :amazonica/client :amazonica/methods}
           (set (keys (meta new)))))
    (is (= new (-> old meta :amazonica/deprecated-in-favor-of))))

  ;; Make sure we don't accidentally attach new metadata keys to old, untouched
  ;; vars:
  (let [unrelated-var #'amazonica.aws.ec2/describe-addresses]
    (is (= (set (keys (meta unrelated-var)))
           #{:ns :name :amazonica/client :amazonica/methods}))))


(deftest keys->cred-tests
  (let [access-key "an access key"
        secret-key "a secret"
        session-token "session token string"
        endpoint "endpoint string"]
    (testing "works with the normal access-key and secret"
      (is (= {:access-key access-key
              :secret-key secret-key}
             (c/keys->cred access-key secret-key))))
    (testing "if an endpoint is present, tacks it on"
      (is (= {:access-key access-key
              :secret-key secret-key
              :endpoint endpoint}
             (c/keys->cred access-key secret-key endpoint)
             (c/keys->cred access-key secret-key endpoint nil))))
    (testing "if a session token is present, tacks it on as well."
      (is (= {:access-key access-key
              :secret-key secret-key
              :session-token session-token}
             (c/keys->cred access-key secret-key nil session-token))))
    (testing "works for all optional posargs."
      (is (= {:access-key access-key
              :secret-key secret-key
              :session-token session-token
              :endpoint endpoint}
             (c/keys->cred access-key secret-key endpoint session-token))))))


(deftest with-credential-tests
  ;; the with-credential macro works by setting the dynamic, thread local binding amazonica.core/*credential*.
  (let [access-key "access"
        secret-key "secret"
        session-token "token"
        endpoint "endpoint"]
    (testing "binds the access and secret key"
      (c/with-credential [access-key secret-key]
        (is (= {:access-key access-key
                :secret-key secret-key}
               @#'amazonica.core/*credentials*))))
    (testing "binds an optional endpoint"
      (c/with-credential [access-key secret-key endpoint]
        (is (= {:access-key access-key
                :secret-key secret-key
                :endpoint endpoint}
               @#'amazonica.core/*credentials*))))
    (testing "binds a session token and no endpoint"
      (c/with-credential [access-key secret-key nil session-token]
        (is (= {:access-key access-key
                :secret-key secret-key
                :session-token session-token}
               @#'amazonica.core/*credentials*))))
    (testing "binds an endpoint and session token"
      (c/with-credential [access-key secret-key endpoint session-token]
        (is (= {:access-key access-key
                :secret-key secret-key
                :endpoint endpoint
                :session-token session-token}
               @#'amazonica.core/*credentials*))))))
