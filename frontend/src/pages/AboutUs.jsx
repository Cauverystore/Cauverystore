import React from "react";
import { Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Shield, Truck, IndianRupee, BadgeCheck, Store, HeartHandshake, ArrowRight } from "lucide-react";
import "../styles/aboutUs.css";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

const facts = [
  {
    value: "Coimbatore",

    get label() {
      return i18n.t("aboutUs.whereWeAreBasedIn");
    }
  },
  {
    value: "₹500",

    get label() {
      return i18n.t("aboutUs.ordersFromThisAmountShip");
    }
  },
  {
    value: "7 days",

    get label() {
      return i18n.t("aboutUs.toReturnMostItemsAfter");
    }
  },
  {
    value: "Mon–Sat",

    get label() {
      return i18n.t("aboutUs.support9AmTo6");
    }
  },
];

const features = [
  {
    icon: Store,

    get title() {
      return i18n.t("aboutUs.approvedListings");
    },

    get text() {
      return i18n.t("aboutUs.everySellerIsCheckedBefore");
    }
  },
  {
    icon: BadgeCheck,

    get title() {
      return i18n.t("aboutUs.clearPrices");
    },

    get text() {
      return i18n.t("aboutUs.thePriceGstAndDelivery");
    }
  },
  {
    icon: Shield,

    get title() {
      return i18n.t("aboutUs.securePayments");
    },

    get text() {
      return i18n.t("aboutUs.payByUpiCardNet");
    }
  },
  {
    icon: Truck,

    get title() {
      return i18n.t("aboutUs.deliveryTimesShownUpfront");
    },

    get text() {
      return i18n.t("aboutUs.deliveryTimesAreShownAt");
    }
  },
  {
    icon: HeartHandshake,

    get title() {
      return i18n.t("aboutUs.7DayReturnsAndSupport");
    },

    get text() {
      return i18n.t("aboutUs.returnMostItemsWithin7");
    }
  },
  {
    icon: IndianRupee,

    get title() {
      return i18n.t("aboutUs.bulkAndCorporateOrders");
    },

    get text() {
      return i18n.t("aboutUs.buyingInQuantityEmailSupport");
    }
  },
];

const values = [
  {
    get title() {
      return i18n.t("aboutUs.honesty");
    },

    get text() {
      return i18n.t("aboutUs.pricesDeliveryChargesAndReturn");
    }
  },
  {
    get title() {
      return i18n.t("aboutUs.customersFirst");
    },

    get text() {
      return i18n.t("aboutUs.ourSupportTeamAnswersMonday");
    }
  },
  {
    get title() {
      return i18n.t("aboutUs.reliability");
    },

    get text() {
      return i18n.t("aboutUs.deliveryEstimatesAreRealisticSo");
    }
  },
  {
    get title() {
      return i18n.t("aboutUs.quality");
    },

    get text() {
      return i18n.t("aboutUs.listingsAreReviewedBeforeThey");
    }
  },
];

const faqs = [
  {
    get q() {
      return i18n.t("aboutUs.whereIsCauveryStoreBased");
    },

    get a() {
      return i18n.t("aboutUs.weAreBasedInCoimbatore");
    }
  },
  {
    get q() {
      return i18n.t("aboutUs.howDoYouEnsureProduct");
    },

    get a() {
      return i18n.t("aboutUs.everySellerIsCheckedBefore1qt");
    }
  },
  {
    get q() {
      return i18n.t("aboutUs.doYouOfferBulkOr");
    },

    get a() {
      return i18n.t("aboutUs.yesBulkAndCorporateOrders");
    }
  },
  {
    get q() {
      return i18n.t("aboutUs.canISellOnCauvery");
    },

    get a() {
      return i18n.t("aboutUs.yesSellersBasedInIndia");
    }
  },
  {
    get q() {
      return i18n.t("aboutUs.whatPaymentMethodsDoYou");
    },

    get a() {
      return i18n.t("aboutUs.upiCreditAndDebitCards");
    }
  },
];

const AboutUs = () => {
  const { t } = useTranslation();

  return (
    <div className="about-page">
      <Helmet>
        <title>{t("aboutUs.aboutUsCauveryStore")}</title>
        <meta name="description" content={t("aboutUs.cauveryStoreIsAnOnline")} />
        <link rel="canonical" href="https://cauverystore.in/about" />
        <meta property="og:title" content={t("footer.about")} />
        <meta property="og:description" content={t("aboutUs.anOnlineStoreBasedIn")} />
        <meta property="og:type" content="website" />
        <meta property="og:url" content="https://cauverystore.in/about" />
        <meta property="og:image" content="https://cauverystore.in/logo512.png" />
      </Helmet>

      <header className="about-hero">
        <svg className="about-river" viewBox="0 0 600 500" fill="none" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
          <path d="M620 20C470 60 520 170 380 210S250 330 120 360 20 470 -20 500" stroke="currentColor" strokeWidth="1.25" />
          <path d="M620 60C490 95 540 200 400 240S270 360 140 390 40 490 0 520" stroke="currentColor" strokeWidth="1" opacity="0.6" />
          <path d="M620 100C510 130 560 230 420 270S290 390 160 420 60 510 20 540" stroke="currentColor" strokeWidth="0.75" opacity="0.35" />
        </svg>
        <div className="about-wrap">
          <h1>{t("footer.about")}</h1>
          <p className="about-display">{t("aboutUs.anOnlineStoreBasedIn1kr")}{" "}<em>{t("aboutUs.acrossIndia")}</em>.
                      </p>
          <dl className="about-facts">
            {facts.map((f) => (
              <div key={f.value}>
                <dt>{f.value}</dt>
                <dd>{f.label}</dd>
              </div>
            ))}
          </dl>
        </div>
      </header>

      <section className="about-section">
        <div className="about-wrap about-split">
          <div>
            <span className="about-label">{t("aboutUs.ourStory")}</span>
            <h2>{t("aboutUs.welcomeToCauveryStore")}</h2>
          </div>
          <div className="about-prose">
            <p className="about-lead">{t("aboutUs.cauveryStoreIsAnOnlinei03")}</p>
            <p>{t("aboutUs.weTakeOurNameFrom")}</p>
            <p>{t("aboutUs.weAreStartingWithT")}</p>
          </div>
        </div>
      </section>

      <section className="about-wrap">
        <div className="about-panel">
          <span className="about-label">{t("aboutUs.howItWorks")}</span>
          <h2>{t("aboutUs.fromOrder")}{" "}<em>{t("aboutUs.toYourDoor")}</em></h2>
          <div className="about-panel-cols">
            <p>{t("aboutUs.chooseWhatYouLikeAnd")}</p>
            <p>{t("aboutUs.cauveryStoreIsAMarketplace")}</p>
          </div>
        </div>
      </section>

      <section className="about-section">
        <div className="about-wrap about-pair">
          <article>
            <span className="about-num">01</span>
            <h2>{t("aboutUs.ourMission")}</h2>
            <p>{t("aboutUs.toMakeOnlineShoppingStraightforwar")}</p>
          </article>
          <article>
            <span className="about-num">02</span>
            <h2>{t("aboutUs.ourVision")}</h2>
            <p>{t("aboutUs.toBeTheOnlineStore")}</p>
          </article>
        </div>
      </section>

      <section className="about-section" style={{ paddingTop: 0 }}>
        <div className="about-wrap">
          <div className="about-section-head">
            <div>
              <span className="about-label">{t("aboutUs.whatYouGet")}</span>
              <h2>{t("aboutUs.whyShopWithUs")}</h2>
            </div>
          </div>
          <ul className="about-features">
            {features.map((f) => {
              const Icon = f.icon;
              return (
                <li key={f.title}>
                  <span className="about-feature-icon"><Icon size={20} strokeWidth={1.6} aria-hidden="true" /></span>
                  <div>
                    <h3>{f.title}</h3>
                    <p>{f.text}</p>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      </section>

      <section className="about-section" style={{ paddingTop: 0 }}>
        <div className="about-wrap">
          <div className="about-section-head">
            <div>
              <span className="about-label">{t("aboutUs.whatWeStandFor")}</span>
              <h2>{t("aboutUs.ourValues")}</h2>
            </div>
          </div>
          <ol className="about-values">
            {values.map((v, i) => (
              <li key={v.title}>
                <span className="about-num">{String(i + 1).padStart(2, "0")}</span>
                <h3>{v.title}</h3>
                <p>{v.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="about-section" style={{ paddingTop: 0 }}>
        <div className="about-wrap about-split">
          <div>
            <span className="about-label">{t("aboutUs.goodToKnow")}</span>
            <h2>{t("aboutUs.frequentlyAskedQuestions")}</h2>
          </div>
          <div className="about-faq">
            {faqs.map((item, i) => (
              <AccordionItem key={item.q} id={`about-faq-${i}`} question={item.q} answer={item.a} />
            ))}
          </div>
        </div>
      </section>

      <section className="about-closing">
        <div className="about-wrap">
          <p className="about-closing-line">{t("aboutUs.thankYouForShoppingWith")}</p>
          <div className="about-actions">
            <Link className="about-cta" to="/products">{t("cartDrawer.browseProducts")}{" "}<ArrowRight size={18} strokeWidth={1.8} aria-hidden="true" />
            </Link>
            <Link className="about-link" to="/seller/register">{t("footer.sellOn")}</Link>
            <Link className="about-link" to="/contact">{t("aboutUs.contactUs")}</Link>
          </div>
        </div>
      </section>
    </div>
  );
};

const AccordionItem = ({ id, question, answer }) => {
  const [open, setOpen] = React.useState(false);
  return (
    <div className={`about-faq-item${open ? " open" : ""}`}>
      <button
        className="about-faq-trigger"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen(!open)}
      >
        <span>{question}</span>
        <span className="about-faq-mark" aria-hidden="true" />
      </button>
      <div className="about-faq-body" id={id}>
        <div>
          <p>{answer}</p>
        </div>
      </div>
    </div>
  );
};

export default AboutUs;
