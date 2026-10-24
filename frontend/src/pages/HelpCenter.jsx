import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { canonicalFor } from "../i18n/canonical";
import { Search, Package, Truck, RotateCcw, User, CreditCard, Headphones, ChevronDown } from "lucide-react";
import StaticLayout from "../components/StaticLayout";
import api from "../api/axios";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

const categories = [
  {
    id: "orders",

    get label() {
      return i18n.t("nav.ordersShort");
    },

    icon: Package
  },
  {
    id: "shipping",

    get label() {
      return i18n.t("helpCenter.shippingDelivery");
    },

    icon: Truck
  },
  {
    id: "returns",

    get label() {
      return i18n.t("helpCenter.returnsRefunds");
    },

    icon: RotateCcw
  },
  {
    id: "account",

    get label() {
      return i18n.t("helpCenter.accountProfile");
    },

    icon: User
  },
  {
    id: "payments",

    get label() {
      return i18n.t("helpCenter.payments");
    },

    icon: CreditCard
  },
];

const fallbackFaqs = {
  orders: [
    {
      get q() {
        return i18n.t("helpCenter.howDoIPlaceAn");
      },

      get a() {
        return i18n.t("helpCenter.browseTheStoreAddItems");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.canICancelMyOrder");
      },

      get a() {
        return i18n.t("helpCenter.yesFreeOfChargeAny");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.howDoITrackMy");
      },

      get a() {
        return i18n.t("helpCenter.openMyOrdersInYour");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.whatIfIReceiveA");
      },

      get a() {
        return i18n.t("helpCenter.raiseAReturnFromMy");
      }
    },
  ],
  shipping: [
    {
      get q() {
        return i18n.t("helpCenter.whatAreTheShippingCharges");
      },

      get a() {
        return i18n.t("helpCenter.deliveryIsFreeOnOrders");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.howLongDoesDeliveryTake");
      },

      get a() {
        return i18n.t("helpCenter.sellersNormallyDispatchWithin1");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.doYouShipInternationally");
      },

      get a() {
        return i18n.t("helpCenter.noWeDeliverToAddresses");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.canIChangeMyDelivery");
      },

      get a() {
        return i18n.t("helpCenter.youCanUpdateTheAddress");
      }
    },
  ],
  returns: [
    {
      get q() {
        return i18n.t("helpCenter.whatIsTheReturnPolicy");
      },

      get a() {
        return i18n.t("helpCenter.mostItemsCanBeReturned");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.howDoIInitiateA");
      },

      get a() {
        return i18n.t("helpCenter.goToYourOrdersPage");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.whenWillIGetMy");
      },

      get a() {
        return i18n.t("helpCenter.refundsAreProcessedWithin5");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.areThereAnyItemsThat");
      },

      get a() {
        return i18n.t("helpCenter.yesInnerwearSwimwearPerishableGood");
      }
    },
  ],
  account: [
    {
      get q() {
        return i18n.t("helpCenter.howDoICreateAn");
      },

      get a() {
        return i18n.t("helpCenter.clickOnLoginAndThen");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.iForgotMyPasswordWhat");
      },

      get a() {
        return i18n.t("helpCenter.onTheLoginPageClick");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.howDoIUpdateMy");
      },

      get a() {
        return i18n.t("helpCenter.logInAndVisitThe");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.canIDeleteMyAccount");
      },

      get a() {
        return i18n.t("helpCenter.yesPleaseContactOurSupport");
      }
    },
  ],
  payments: [
    {
      get q() {
        return i18n.t("aboutUs.whatPaymentMethodsDoYou");
      },

      get a() {
        return i18n.t("helpCenter.upiCreditAndDebitCards");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.isMyPaymentInformationSecure");
      },

      get a() {
        return i18n.t("helpCenter.absolutelyAllPaymentsAreProcessed");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.whatShouldIDoIf");
      },

      get a() {
        return i18n.t("helpCenter.firstCheckIfTheAmount");
      }
    },
    {
      get q() {
        return i18n.t("helpCenter.doYouOfferCashOn");
      },

      get a() {
        return i18n.t("helpCenter.notAtPresentAllOrders");
      }
    },
  ],
};

const HelpCenter = () => {
  const {
    t: tr
  } = useTranslation();

  const [activeTab, setActiveTab] = useState("orders");
  const [faqs, setFaqs] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get("/api/faqs").then((r) => {
      if (r.data && Array.isArray(r.data)) {
        const grouped = {};
        const seen = new Set();
        r.data.forEach((f) => {
          // FAQs saved without one of the tab categories go under "general" so they
          // still show; the same question saved twice is only listed once.
          const cat = categories.some((c) => c.id === f.category) ? f.category : "general";
          const key = cat + "|" + (f.question || "").trim().toLowerCase();
          if (!f.question || seen.has(key)) return;
          seen.add(key);
          if (!grouped[cat]) grouped[cat] = [];
          grouped[cat].push({ q: f.question, a: f.answer });
        });
        if (Object.keys(grouped).length > 0) setFaqs(grouped);
      }
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const data = faqs || fallbackFaqs;
  // Only offer tabs that have questions, so no tab opens onto an empty list.
  const tabs = [{ id: "general", label: tr("helpCenter.general") }, ...categories]
    .filter((c) => data[c.id]?.length > 0)
    .map((c) => ({ id: c.id, label: c.label }));
  const currentTab = tabs.some((t) => t.id === activeTab) ? activeTab : tabs[0]?.id;
  const currentFaqs = data[currentTab] || [];

  // Describes every question the page offers (all tabs), built from the same data
  // that is rendered, so the markup cannot drift from what visitors see. Skipped for
  // the built-in fallback list, which only shows when the FAQ service is unreachable.
  const faqJsonLd = !faqs ? null : {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    "mainEntity": tabs.flatMap((t) => data[t.id]).map((item) => ({
      "@type": "Question",
      "name": item.q,
      "acceptedAnswer": { "@type": "Answer", "text": item.a },
    })),
  };

  const filtered = searchTerm.trim()
    ? currentFaqs.filter(
        (item) =>
          item.q.toLowerCase().includes(searchTerm.toLowerCase()) ||
          item.a.toLowerCase().includes(searchTerm.toLowerCase())
      )
    : currentFaqs;

  return (
    <StaticLayout
      hero={{
        title: tr("footer.helpCenter"),
        subtitle: tr("helpCenter.findAnswersToCommonQuestions"),
      }}
      tabs={tabs}
      activeTab={currentTab}
      onTabChange={setActiveTab}
    >
      <Helmet>
        <title>{tr("helpCenter.helpCenterCauveryStore")}</title>
        {/* /faq and /help both render this page; /faq is the one in the sitemap. */}
        <link rel="canonical" href={canonicalFor("/faq")} />
        <meta name="description" content={tr("helpCenter.getAnswersToFrequentlyAsked")} />
        {faqJsonLd && faqJsonLd.mainEntity.length > 0 && (
          <script type="application/ld+json">{JSON.stringify(faqJsonLd).replace(/</g, "\\u003c")}</script>
        )}
      </Helmet>

      <div className="static-search">
        <Search size={18} className="static-search-icon" />
        <input
          type="text"
          placeholder={tr("helpCenter.searchWithinThisSection")}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>

      {loading ? (
        <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>{tr("helpCenter.loadingFaqs")}</p>
      ) : filtered.length === 0 ? (
        <p style={{ textAlign: "center", color: "#94a3b8", padding: "2rem" }}>
          {searchTerm.trim() ? i18n.t("msgs.noFaqsFor", { term: searchTerm }) : tr("helpCenter.noFaqsAvailableAtThe")}
        </p>
      ) : (
        <div className="static-accordion">
          {filtered.map((item, i) => (
            <AccordionItem key={i} question={item.q} answer={item.a} />
          ))}
        </div>
      )}

      <div className="static-section" style={{ textAlign: "center", marginTop: "2.5rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>{tr("helpCenter.stillNeedHelp")}</h3>
        <p style={{ marginBottom: "1rem" }}>{tr("helpCenter.ourSupportTeamIsAvailable")}</p>
        <a href="mailto:support@cauverystore.in" className="static-btn static-btn-outline">
          <Headphones size={16} />{" "}{tr("helpCenter.contactSupport")}</a>
      </div>
    </StaticLayout>
  );
};

const AccordionItem = ({ question, answer }) => {
  const [open, setOpen] = useState(false);
  return (
    <div className="static-accordion-item">
      <button className="static-accordion-trigger" onClick={() => setOpen(!open)}>
        <span>{question}</span>
        <ChevronDown size={16} className={`static-accordion-arrow${open ? " open" : ""}`} />
      </button>
      <div className={`static-accordion-body${open ? " open" : ""}`}>
        <p>{answer}</p>
      </div>
    </div>
  );
};

export default HelpCenter;
