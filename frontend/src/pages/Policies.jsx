import React from "react";
import { Helmet } from "react-helmet-async";
import { Link, useNavigate } from "react-router-dom";
import StaticLayout from "../components/StaticLayout";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

// Each policy has its own address. Payment partners (Razorpay) and search engines check a
// policy by its URL, and a tab chosen by a "#terms" suffix all looks like one page to them.
const tabs = [
  {
    id: "privacy",

    get label() {
      return i18n.t("footer.privacy");
    },

    path: "/privacy-policy"
  },
  {
    id: "terms",

    get label() {
      return i18n.t("footer.terms");
    },

    path: "/terms-and-conditions"
  },
  {
    id: "shipping",

    get label() {
      return i18n.t("footer.shipping");
    },

    path: "/shipping-policy"
  },
];

const UPDATED = "10 October 2026";

const policies = {
  privacy: {
    get title() {
      return i18n.t("footer.privacy");
    },

    updated: "20 October 2026",

    sections: [
      {
        get heading() {
          return i18n.t("policies.informationWeCollect");
        },

        get content() {
          return i18n.t("policies.weCollectInformationYouProvide");
        }
      },
      {
        get heading() {
          return i18n.t("policies.paymentInformation");
        },

        get items() {
          return [
            i18n.t("policies.onlinePaymentsAreHandledBy"),
            i18n.t("policies.weDoNotReceiveOr"),
            i18n.t("policies.razorpayTellsUsWhetherThe"),
            i18n.t("policies.razorpayHandlesYourPaymentDetails")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.howWeUseYourInformation");
        },

        get items() {
          return [
            i18n.t("policies.processAndFulfillYourOrders"),
            i18n.t("policies.provideCustomerSupportAndRespond"),
            i18n.t("policies.sendPersonalisedProductRecommendat"),
            i18n.t("policies.improveOurWebsiteProductsAnd"),
            i18n.t("policies.preventFraudAndEnsureThe")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.informationSharing");
        },

        get content() {
          return i18n.t("policies.weDoNotSellYour");
        }
      },
      {
        get heading() {
          return i18n.t("policies.dataSecurity");
        },

        get content() {
          return i18n.t("policies.weImplementIndustryStandardSecurit");
        }
      },
      {
        get heading() {
          return i18n.t("policies.yourRights");
        },

        get items() {
          return [
            i18n.t("policies.accessUpdateOrDeleteYour"),
            i18n.t("policies.withdrawConsentForMarketingCommuni"),
            i18n.t("policies.requestACopyOfThe"),
            i18n.t("policies.fileAComplaintWithThe")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.cookies");
        },

        get content() { return i18n.t("msgs.policyCookies"); }
      },
      {
        get heading() {
          return i18n.t("footer.contact");
        },

        get content() {
          return i18n.t("policies.forPrivacyRelatedQueriesPlease");
        }
      }
    ]
  },
  terms: {
    get title() {
      return i18n.t("footer.terms");
    },

    updated: UPDATED,

    sections: [
      {
        get heading() {
          return i18n.t("policies.acceptanceOfTerms");
        },

        get content() {
          return i18n.t("policies.byAccessingOrUsingCauvery");
        }
      },
      {
        get heading() {
          return i18n.t("policies.eligibility");
        },

        get content() {
          return i18n.t("policies.youMustBeAtLeast");
        }
      },
      {
        get heading() {
          return i18n.t("policies.accountRegistration");
        },

        get items() {
          return [
            i18n.t("policies.youAreResponsibleForMaintaining"),
            i18n.t("policies.youMustProvideAccurateCurrent"),
            i18n.t("policies.youAreResponsibleForAll"),
            i18n.t("policies.notifyUsImmediatelyOfAny")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.howTheMarketplaceWorks");
        },

        get content() {
          return i18n.t("policies.cauveryStoreIsAnOnline");
        }
      },
      {
        get heading() {
          return i18n.t("policies.pricesAndOrders");
        },

        get items() {
          return [
            i18n.t("policies.allPricesAreInIndian"),
            i18n.t("policies.allOrdersAreSubjectTo"),
            i18n.t("policies.weOrTheSellerMay")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("helpCenter.payments");
        },

        get items() {
          return [
            i18n.t("policies.onlinePaymentsAreProcessedBy"),
            i18n.t("policies.youCanPayUsingThe"),
            i18n.t("policies.youMustUseAPayment"),
            i18n.t("policies.yourCardUpiAndBank"),
            i18n.t("policies.anOrderPaidOnlineIs"),
            i18n.t("policies.yourBankOrCardIssuer")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.failedAndDuplicatePayments");
        },

        get items() {
          return [
            i18n.t("policies.ifAPaymentFailsNo"),
            i18n.t("policies.ifMoneyLeavesYourAccount"),
            i18n.t("policies.ifYouAreChargedTwice"),
            i18n.t("policies.weAreNotResponsibleFor")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.cancellationsReturnsAndRefunds");
        },

        get content() {
          return i18n.t("policies.youCanCancelAnOrder");
        },

        link: {
          to: "/refund-policy",

          get label() {
            return i18n.t("policies.readTheReturnsCancellationsRefunds");
          }
        }
      },
      {
        get heading() {
          return i18n.t("cart.delivery");
        },

        get content() {
          return i18n.t("policies.deliveryAreasChargesAndTimeframes");
        },

        link: {
          to: "/shipping-policy",

          get label() {
            return i18n.t("policies.readTheShippingPolicy");
          }
        }
      },
      {
        get heading() {
          return i18n.t("policies.prohibitedUse");
        },

        get items() {
          return [
            i18n.t("policies.doNotUseTheSite"),
            i18n.t("policies.doNotAttemptToInterfere"),
            i18n.t("policies.weMaySuspendAnAccount")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.intellectualProperty");
        },

        get content() {
          return i18n.t("policies.allContentOnCauveryStore");
        }
      },
      {
        get heading() {
          return i18n.t("policies.limitationOfLiability");
        },

        get content() {
          return i18n.t("policies.cauveryStoreShallNotBe");
        }
      },
      {
        get heading() {
          return i18n.t("policies.complaintsAndContact");
        },

        get content() {
          return i18n.t("policies.ifSomethingHasGoneWrong");
        },

        link: {
          to: "/contact",

          get label() {
            return i18n.t("aboutUs.contactUs");
          }
        }
      },
      {
        get heading() {
          return i18n.t("policies.governingLaw");
        },

        get content() {
          return i18n.t("policies.theseTermsAreGovernedBy");
        }
      }
    ]
  },
  // Several couriers are used, so nothing here names one or relies on one courier's terms.
  // Delivery times below are estimates built from published courier norms (India Post Speed
  // Post service standards and the usual private-courier ranges), not a contract. Check them
  // against the courier agreement actually in use before changing a number. Charges mirror
  // the cart: free from Rs. 500, otherwise Rs. 40.
  shipping: {
    get title() {
      return i18n.t("footer.shipping");
    },

    updated: UPDATED,

    sections: [
      {
        get heading() {
          return i18n.t("policies.whereWeDeliver");
        },

        get items() {
          return [
            i18n.t("policies.cauveryStoreIsAMarketplace"),
            i18n.t("policies.theCourierForYourOrder"),
            i18n.t("policies.weDeliverToAddressesIn"),
            i18n.t("policies.whetherAProductCanBe")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.deliveryCharges");
        },

        get items() {
          return [
            i18n.t("policies.freeDeliveryOnOrdersOf"),
            i18n.t("policies.aFlatRs40Delivery"),
            i18n.t("policies.theDeliveryChargeIsShown")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.howLongDeliveryTakes");
        },

        get content() {
          return i18n.t("policies.deliveryTimeHasTwoParts");
        },

        get items() {
          return [
            i18n.t("policies.dispatchSellersNormallyHandYour"),
            i18n.t("policies.withinTheSellerSOwn"),
            i18n.t("policies.neighbouringStatesUsually35"),
            i18n.t("policies.restOfIndiaUsually4"),
            i18n.t("policies.remoteAreasTheNorthEast")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.aboutTheseEstimates");
        },

        get items() {
          return [
            i18n.t("policies.theseAreEstimatesNotGuaranteed"),
            i18n.t("policies.businessDaysAreMondayTo"),
            i18n.t("policies.deliveryCanTakeLongerDuring"),
            i18n.t("policies.ifYourOrderIsRunning")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.ordersFromMoreThanOne");
        },

        get content() {
          return i18n.t("policies.ifYourOrderHasProducts");
        }
      },
      {
        get heading() {
          return i18n.t("policies.trackingYourOrder");
        },

        get items() {
          return [
            i18n.t("policies.youCanFollowEveryStage"),
            i18n.t("policies.onceYourOrderShipsThe"),
            i18n.t("policies.weEmailYouWhenYour"),
            i18n.t("policies.courierTrackingCanTakeUp")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.changingOrCancellingBeforeDispatch");
        },

        get items() {
          return [
            i18n.t("policies.youCanCancelAnOrder18w"),
            i18n.t("policies.toCorrectADeliveryAddress")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.whenYourOrderArrives");
        },

        get items() {
          return [
            i18n.t("policies.pleaseMakeSureSomeoneIs"),
            i18n.t("policies.checkTheParcelBeforeAccepting"),
            i18n.t("policies.ifTheItemInsideIs"),
            i18n.t("policies.theRiskInTheGoods")
          ];
        },

        link: {
          to: "/refund-policy",

          get label() {
            return i18n.t("policies.readTheReturnsCancellationsRefunds");
          }
        }
      },
      {
        get heading() {
          return i18n.t("policies.ifDeliveryCannotBeCompleted");
        },

        get items() {
          return [
            i18n.t("policies.theCourierTriesToDeliver"),
            i18n.t("policies.ifDeliveryFailsBecauseThe"),
            i18n.t("policies.forAnOrderYouHave"),
            i18n.t("policies.ifDeliveryFailsForA")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.lateOrMissingOrders");
        },

        get items() {
          return [
            i18n.t("policies.ifYourOrderHasNot"),
            i18n.t("policies.ifAParcelIsConfirmed"),
            i18n.t("policies.ifYourOrderIsMarked")
          ];
        }
      },
      {
        get heading() {
          return i18n.t("policies.contact");
        },

        get content() {
          return i18n.t("policies.forAnyDeliveryQuestionEmail");
        },

        link: {
          to: "/contact",

          get label() {
            return i18n.t("aboutUs.contactUs");
          }
        }
      }
    ]
  }
};

// One description per policy: three pages sharing a sentence look like duplicates in search.
const descriptions = {
  get privacy() { return i18n.t("msgs.policyDescPrivacy"); },
  get terms() { return i18n.t("msgs.policyDescTerms"); },
  get shipping() { return i18n.t("msgs.policyDescShipping"); },
};

const Policies = ({ tab }) => {
  const {
    t: tr
  } = useTranslation();

  const navigate = useNavigate();
  // Old links used /policies#terms; keep them opening the right policy.
  const hashTab = window.location.hash.replace("#", "");
  const wanted = tab || hashTab;
  const activeTab = tabs.some((t) => t.id === wanted) ? wanted : "privacy";
  const setActiveTab = (id) => navigate(tabs.find((t) => t.id === id).path);
  const policy = policies[activeTab];
  const canonicalUrl = "https://cauverystore.in" + tabs.find((t) => t.id === activeTab).path;

  const tabsConfig = tabs.map((t) => ({ id: t.id, label: t.label }));

  return (
    <StaticLayout
      hero={{
        title: policy.title,
        subtitle: tr("policies.understandHowWeHandleYour"),
      }}
      tabs={tabsConfig}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      <Helmet>
        {/* One string, not text plus an expression: Helmet drops a title with several children. */}
        <title>{`${policy.title} | Cauvery Store`}</title>
        <meta name="description" content={descriptions[activeTab]} />
        <link rel="canonical" href={canonicalUrl} />
      </Helmet>

      <p style={{ fontSize: "0.85rem", color: "#94a3b8", marginBottom: "1.5rem" }}>{tr("policies.lastUpdated")}{" "}{policy.updated}
      </p>

      {policy.sections.map((section, i) => (
        <div className="static-section" key={i}>
          <h3>{section.heading}</h3>
          {section.content && <p>{section.content}</p>}
          {section.items && (
            <ul>
              {section.items.map((item, j) => (
                <li key={j}>{item}</li>
              ))}
            </ul>
          )}
          {section.link && <p><Link to={section.link.to}>{section.link.label}</Link></p>}
        </div>
      ))}

      <div className="static-section" style={{ borderTop: "1px solid #e2e8f0", paddingTop: "1.5rem" }}>
        <p style={{ fontSize: "0.85rem", color: "#64748b" }}>{tr("policies.ifYouHaveAnyQuestions")}{" "}
          <a href="mailto:support@cauverystore.in">{tr("contactUs.supportCauverystoreIn")}</a>.
        </p>
      </div>
    </StaticLayout>
  );
};

export default Policies;
