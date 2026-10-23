import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Link } from "react-router-dom";
import { Package, CreditCard, Clock, Truck, HelpCircle, ArrowRight, ShieldCheck, ChevronDown, Mail, XCircle, AlertTriangle, Ban } from "lucide-react";
import "../styles/staticLayout.css";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

const green = "#0E5C5C";
const dark = "#14302f";
const muted = "#48595a";
const border = "1px solid rgba(14, 92, 92, 0.14)";

const LAST_UPDATED = "10 October 2026";

const sidebarLinks = [
  {
    id: "summary",

    get label() {
      return i18n.t("returnsandRefundPolicy.atAGlance");
    },

    icon: ShieldCheck
  },
  {
    id: "returns",

    get label() {
      return i18n.t("returnsandRefundPolicy.returns");
    },

    icon: Package
  },
  {
    id: "non-returnable",

    get label() {
      return i18n.t("returnsandRefundPolicy.nonReturnableItems");
    },

    icon: Ban
  },
  {
    id: "how-to-return",

    get label() {
      return i18n.t("returnsandRefundPolicy.howToReturn");
    },

    icon: Truck
  },
  {
    id: "damaged",

    get label() {
      return i18n.t("returnsandRefundPolicy.damagedOrWrongItems");
    },

    icon: AlertTriangle
  },
  {
    id: "cancellations",

    get label() {
      return i18n.t("returnsandRefundPolicy.cancellations");
    },

    icon: XCircle
  },
  {
    id: "refunds",

    get label() {
      return i18n.t("returnsandRefundPolicy.refunds");
    },

    icon: CreditCard
  },
  {
    id: "faqs",

    get label() {
      return i18n.t("returnsandRefundPolicy.faqs");
    },

    icon: HelpCircle
  },
  {
    id: "contact",

    get label() {
      return i18n.t("footer.contact");
    },

    icon: Mail
  },
];

// The figures here mirror what the order system enforces (ReturnEligibilityService and
// OrderService on the backend) - change them together, or this page promises something
// the Return button will refuse.
const summaryCards = [
  {
    get label() {
      return i18n.t("returnsandRefundPolicy.returnWindow");
    },

    get value() {
      return i18n.t("returnsandRefundPolicy.7Days");
    },

    get note() { return i18n.t("msgs.returnNote1"); },
    color: green
  },
  {
    get label() {
      return i18n.t("returnsandRefundPolicy.faultyOrWrongItem");
    },

    get value() {
      return i18n.t("returnsandRefundPolicy.freePickup");
    },

    get note() { return i18n.t("msgs.returnNote2"); },
    color: green
  },
  {
    get label() {
      return i18n.t("returnsandRefundPolicy.refundGoesTo");
    },

    get value() {
      return i18n.t("returnsandRefundPolicy.originalPaymentMethod");
    },

    get note() { return i18n.t("msgs.returnNote3"); },
    color: green
  },
  {
    get label() {
      return i18n.t("returnsandRefundPolicy.refundTime");
    },

    get value() {
      return i18n.t("returnsandRefundPolicy.57BusinessDays");
    },

    get note() { return i18n.t("msgs.returnNote4"); },
    color: green
  },
];

const returnSteps = [
  {
    get title() {
      return i18n.t("returnsandRefundPolicy.1RequestTheReturn");
    },

    get desc() {
      return i18n.t("returnsandRefundPolicy.goToMyOrdersOpen");
    }
  },
  {
    get title() {
      return i18n.t("returnsandRefundPolicy.2WeReviewIt");
    },

    get desc() {
      return i18n.t("returnsandRefundPolicy.weCheckTheRequestAnd");
    }
  },
  {
    get title() {
      return i18n.t("returnsandRefundPolicy.3TheItemTravelsBack");
    },

    get desc() {
      return i18n.t("returnsandRefundPolicy.packTheItemSecurelyWith");
    }
  },
  {
    get title() {
      return i18n.t("returnsandRefundPolicy.4TheItemIsChecked");
    },

    get desc() {
      return i18n.t("returnsandRefundPolicy.whenTheItemReachesUs");
    }
  },
  {
    get title() {
      return i18n.t("returnsandRefundPolicy.5YourRefundIsSent");
    },

    get desc() {
      return i18n.t("returnsandRefundPolicy.onceTheItemPassesThe");
    }
  },
];

const faqs = [
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.howManyDaysDoI");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.7DaysFromTheDate");
    }
  },
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.whichItemsCannotBeReturned");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.innerwearSwimwearPerishableGoodsSu");
    }
  },
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.whoPaysForReturnShipping");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.ifTheItemIsDamaged");
    }
  },
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.whereWillMyRefundBe");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.toTheSamePaymentMethod");
    }
  },
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.howLongDoesARefund");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.onceWeSendItSome");
    }
  },
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.canICancelAnOrder");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.yesAnyTimeBeforeIt");
    }
  },
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.whatHappensIfMyReturn");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.weEmailYouTheReason");
    }
  },
  {
    get question() {
      return i18n.t("returnsandRefundPolicy.canICancelARefund");
    },

    get answer() {
      return i18n.t("returnsandRefundPolicy.noOnceARefundHas");
    }
  },
];

const Section = ({ id, title, icon: Icon, children }) => (
  <section id={id} className="static-section" style={{ scrollMarginTop: "96px", marginBottom: "2rem" }}>
    <h2 style={{ fontSize: "1.2rem", fontWeight: 700, color: dark, margin: "0 0 14px", display: "flex", alignItems: "center", gap: "8px" }}>
      <Icon size={20} color={green} /> {title}
    </h2>
    {children}
  </section>
);

const ReturnsandRefundPolicy = () => {
  const { t } = useTranslation();

  const [openFaq, setOpenFaq] = useState(null);

  const jumpTo = (id) => {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div className="static-page" style={{ paddingBottom: "80px" }}>
      <div className="static-hero" style={{ borderRadius: 0, minHeight: "auto", padding: "2.5rem 1.5rem" }}>
        <div className="static-hero-content">
          <h1>{t("returnsandRefundPolicy.returnsCancellationsRefunds")}</h1>
          <p>{t("returnsandRefundPolicy.whatYouCanSendBack")}</p>
          <div className="static-hero-actions">
            <Link to="/orders" className="static-btn static-btn-primary">
              <ArrowRight size={16} />{" "}{t("returnsandRefundPolicy.returnOrCancelAnOrder")}</Link>
            <a href="mailto:support@cauverystore.in" className="static-btn static-btn-secondary">
              <Mail size={16} />{" "}{t("returnsandRefundPolicy.emailSupport")}</a>
          </div>
        </div>
      </div>

      <Helmet>
        <title>{t("returnsandRefundPolicy.returnsCancellationsRefundsPolicyC")}</title>
        <meta name="description" content={t("returnsandRefundPolicy.cauveryStoreSReturnCancellation")} />
        <link rel="canonical" href="https://cauverystore.in/refund-policy" />
      </Helmet>

      <div className="static-content" style={{ maxWidth: "1100px" }}>
        <div className="rrp-layout" style={{ display: "flex", gap: "32px", alignItems: "flex-start" }}>
          <nav className="rrp-nav" aria-label={t("returnsandRefundPolicy.policySections")} style={{ width: "240px", flexShrink: 0, position: "sticky", top: "88px", alignSelf: "flex-start" }}>
            <div className="rrp-nav-list" style={{ background: "#fff", borderRadius: "12px", border, padding: "8px", marginBottom: "16px" }}>
              {sidebarLinks.map((link) => (
                <button key={link.id} className="rrp-nav-link" onClick={() => jumpTo(link.id)} style={{
                  display: "flex", alignItems: "center", gap: "10px", width: "100%", padding: "10px 12px",
                  border: "none", background: "transparent", borderRadius: "8px", cursor: "pointer",
                  fontSize: "0.85rem", color: dark, textAlign: "left",
                }}>
                  <link.icon size={18} />
                  {link.label}
                </button>
              ))}
            </div>
            <div className="rrp-nav-help" style={{ background: "#fefce8", borderRadius: "12px", border: "1px solid #fde68a", padding: "14px", fontSize: "0.8rem", color: "#92400e", lineHeight: 1.6 }}>
              <strong style={{ display: "block", marginBottom: "4px" }}>{t("returnsandRefundPolicy.needHelp")}</strong>{t("contactUs.email")}{" "}<strong>{t("contactUs.supportCauverystoreIn")}</strong>. Our team is available Mon-Sat, 9 AM - 6 PM.
                          </div>
          </nav>

          <main style={{ flex: 1, minWidth: 0, background: "#fff", borderRadius: "12px", border, padding: "28px" }}>
            <p style={{ fontSize: "0.8rem", color: muted, margin: "0 0 20px" }}>{t("policies.lastUpdated")}{" "}{LAST_UPDATED}</p>

            <Section id="summary" title={t("returnsandRefundPolicy.atAGlance")} icon={ShieldCheck}>
              <div className="static-card-grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: "12px" }}>
                {summaryCards.map((card) => (
                  <div className="static-card" key={card.label} style={{ padding: "14px" }}>
                    <p style={{ fontSize: "0.75rem", color: muted, marginBottom: "4px" }}>{card.label}</p>
                    <p style={{ fontSize: "1rem", fontWeight: 700, color: card.color, margin: "0 0 4px" }}>{card.value}</p>
                    <p style={{ fontSize: "0.78rem", color: muted, margin: 0 }}>{card.note}</p>
                  </div>
                ))}
              </div>
              <p style={{ marginTop: "14px" }}>{t("returnsandRefundPolicy.cauveryStoreIsAMarketplace")}</p>
            </Section>

            <Section id="returns" title={t("returnsandRefundPolicy.returns")} icon={Package}>
              <h3>{t("returnsandRefundPolicy.returnPeriod")}</h3>
              <ul>
                <li>{t("returnsandRefundPolicy.youCanAskToReturn")}{" "}<strong>{t("returnsandRefundPolicy.7DaysOfDelivery")}</strong>.</li>
                <li>{t("returnsandRefundPolicy.aSellerMaySetA")}</li>
                <li>{t("returnsandRefundPolicy.ifOneOrderContainsItems")}{" "}<strong>{t("returnsandRefundPolicy.shortest")}</strong>{" "}{t("returnsandRefundPolicy.periodAppliesToTheWhole")}</li>
                <li>{t("returnsandRefundPolicy.theReturnMustBeRequested")}</li>
              </ul>

              <h3>{t("returnsandRefundPolicy.conditionOfTheItem")}</h3>
              <p>{t("returnsandRefundPolicy.toBeAcceptedAReturned")}</p>
              <ul>
                <li>{t("returnsandRefundPolicy.unusedUnwashedAndUndamagedOther")}</li>
                <li>{t("returnsandRefundPolicy.inItsOriginalPackagingWith")}</li>
                <li>{t("returnsandRefundPolicy.theSameItemThatWas")}</li>
              </ul>

              <h3>{t("returnsandRefundPolicy.whoPaysForTheReturn")}</h3>
              <div style={{ display: "flex", gap: "16px", flexWrap: "wrap" }}>
                <div className="static-info-box" style={{ flex: 1, minWidth: "220px" }}>
                  <strong>{t("returnsandRefundPolicy.freeReturn")}</strong>{" "}{t("returnsandRefundPolicy.theItemIsDamagedDefective")}</div>
                <div className="static-warning-box" style={{ flex: 1, minWidth: "220px" }}>
                  <strong>{t("returnsandRefundPolicy.youPayReturnShipping")}</strong>{" "}{t("returnsandRefundPolicy.anyOtherReasonSuchAs")}</div>
              </div>
            </Section>

            <Section id="non-returnable" title={t("returnsandRefundPolicy.nonReturnableItems")} icon={Ban}>
              <p>{t("returnsandRefundPolicy.forHygieneAndSafetyReasons")}</p>
              <ul>
                <li>{t("returnsandRefundPolicy.innerwearLingerieAndSwimwear")}</li>
                <li>{t("returnsandRefundPolicy.perishableGoodsSuchAsFresh")}</li>
                <li>{t("returnsandRefundPolicy.clearanceItems")}</li>
                <li>{t("returnsandRefundPolicy.customisedPersonalisedOrMadeTo")}</li>
                <li>{t("returnsandRefundPolicy.anyProductMarked")}{" "}<strong>{t("returnsandRefundPolicy.noReturns")}</strong>{" "}{t("returnsandRefundPolicy.onItsProductPage")}</li>
              </ul>
              <div className="static-info-box">{t("returnsandRefundPolicy.ifOneOfTheseArrives")}{" "}
                <a href="mailto:support@cauverystore.in">{t("contactUs.supportCauverystoreIn")}</a>{" "}{t("returnsandRefundPolicy.withYourOrderNumberAnd")}{" "}<strong>{t("returnsandRefundPolicy.damagedOrWrongItems")}</strong>{" "}{t("returnsandRefundPolicy.below")}</div>
            </Section>

            <Section id="how-to-return" title={t("returnsandRefundPolicy.howToReturnAnItem")} icon={Truck}>
              <div className="static-timeline">
                {returnSteps.map((step) => (
                  <div className="static-timeline-step" key={step.title}>
                    <div className="static-timeline-dot" />
                    <h4>{step.title}</h4>
                    <p>{step.desc}</p>
                  </div>
                ))}
              </div>
              <p>{t("returnsandRefundPolicy.weEmailYouAtEvery")}{" "}<Link to="/orders">{t("nav.orders")}</Link>.
                              </p>
              <h3>{t("returnsandRefundPolicy.ifAReturnIsNot")}</h3>
              <ul>
                <li>{t("returnsandRefundPolicy.aRequestMayBeDeclined")}</li>
                <li>{t("returnsandRefundPolicy.aReturnedItemMayFail")}</li>
                <li>{t("returnsandRefundPolicy.weAlwaysEmailYouThe")}</li>
              </ul>
            </Section>

            <Section id="damaged" title={t("returnsandRefundPolicy.damagedDefectiveOrWrongItems")} icon={AlertTriangle}>
              <ul>
                <li>{t("returnsandRefundPolicy.pleaseCheckYourParcelWhen")}</li>
                <li>{t("returnsandRefundPolicy.ifSomethingIsDamagedDefective")}{" "}<Link to="/orders">{t("nav.orders")}</Link>{" "}{t("returnsandRefundPolicy.andChooseTheMatchingReason")}{" "}<a href="mailto:support@cauverystore.in">{t("contactUs.supportCauverystoreIn")}</a>.</li>
                <li>{t("returnsandRefundPolicy.includeYourOrderNumberAnd")}</li>
                <li>{t("returnsandRefundPolicy.tellUsAsEarlyAs")}</li>
                <li>{t("returnsandRefundPolicy.theReturnIsFreeAnd")}</li>
              </ul>
            </Section>

            <Section id="cancellations" title={t("returnsandRefundPolicy.cancellations")} icon={XCircle}>
              <h3>{t("returnsandRefundPolicy.cancellingYourOrder")}</h3>
              <ul>
                <li>{t("returnsandRefundPolicy.youCanCancelAnOrder")}{" "}<Link to="/orders">{t("nav.orders")}</Link>{" "}{t("returnsandRefundPolicy.atAnyTime")}{" "}<strong>{t("returnsandRefundPolicy.beforeItIsShipped")}</strong>. There is no cancellation fee.</li>
                <li>{t("returnsandRefundPolicy.ifYouHadAlreadyPaid")}{" "}<strong>{t("returnsandRefundPolicy.fullAmount")}</strong>{" "}{t("returnsandRefundPolicy.isRefundedAutomaticallyToYour")}</li>
                <li>{t("returnsandRefundPolicy.onceAnOrderHasShipped")}</li>
              </ul>
              <h3>{t("returnsandRefundPolicy.ifWeCancelYourOrder")}</h3>
              <p>{t("returnsandRefundPolicy.weOrTheSellerMay")}</p>
            </Section>

            <Section id="refunds" title={t("returnsandRefundPolicy.refunds")} icon={CreditCard}>
              <h3>{t("returnsandRefundPolicy.howRefundsArePaid")}</h3>
              <ul>
                <li>{t("returnsandRefundPolicy.refundsAreProcessedThrough")}{" "}<strong>Razorpay</strong>{t("returnsandRefundPolicy.ourPaymentPartner")}</li>
                <li>{t("returnsandRefundPolicy.refundsAlwaysGoBackTo")}{" "}<strong>{t("returnsandRefundPolicy.originalPaymentMethod1hn")}</strong>{t("returnsandRefundPolicy.theSameCardUpiId")}</li>
                <li>{t("returnsandRefundPolicy.aRefundThatHasBeen")}</li>
              </ul>
              <h3>{t("returnsandRefundPolicy.whenRefundsAreSent")}</h3>
              <ul>
                <li><strong>{t("returnsandRefundPolicy.cancelledOrders")}</strong>{" "}{t("returnsandRefundPolicy.asSoonAsTheOrder")}</li>
                <li><strong>{t("returnsandRefundPolicy.returnedItems")}</strong>{" "}{t("returnsandRefundPolicy.onceTheItemHasReached")}</li>
              </ul>
              <h3>{t("returnsandRefundPolicy.howLongItTakesTo")}</h3>
              <ul>
                <li>{t("returnsandRefundPolicy.someRefundsAreCredited")}{" "}<strong>{t("returnsandRefundPolicy.withinMinutes")}</strong>{t("returnsandRefundPolicy.whereYourBankSupportsIt")}</li>
                <li>{t("returnsandRefundPolicy.otherwiseAllow")}{" "}<strong>{t("returnsandRefundPolicy.57BusinessDays")}</strong>{" "}{t("returnsandRefundPolicy.forYourBankToCredit")}</li>
                <li>{t("returnsandRefundPolicy.weEmailYouWhenThe")}</li>
              </ul>
              <div className="static-info-box">
                <strong><Clock size={14} style={{ display: "inline", verticalAlign: "-2px" }} />{" "}{t("returnsandRefundPolicy.refundNotArrived")}</strong>{" "}{t("returnsandRefundPolicy.ifItHasNotReached")}{" "}
                <a href="mailto:support@cauverystore.in">{t("contactUs.supportCauverystoreIn")}</a>{" "}{t("returnsandRefundPolicy.withYourOrderNumberAnd1oq")}</div>
            </Section>

            <Section id="faqs" title={t("returnsandRefundPolicy.frequentlyAskedQuestions")} icon={HelpCircle}>
              <div className="static-accordion" style={{ marginTop: 0 }}>
                {faqs.map((item, idx) => (
                  <div className="static-accordion-item" key={item.question}>
                    <button className="static-accordion-trigger" aria-expanded={openFaq === idx} onClick={() => setOpenFaq(openFaq === idx ? null : idx)}>
                      <span>{item.question}</span>
                      <ChevronDown size={16} className={`static-accordion-arrow${openFaq === idx ? " open" : ""}`} style={{ color: green }} />
                    </button>
                    <div className={`static-accordion-body${openFaq === idx ? " open" : ""}`}>
                      <p>{item.answer}</p>
                    </div>
                  </div>
                ))}
              </div>
            </Section>

            <Section id="contact" title={t("footer.contact")} icon={Mail}>
              <p>{t("returnsandRefundPolicy.forAnyQuestionAboutA")}{" "}
                <a href="mailto:support@cauverystore.in">{t("contactUs.supportCauverystoreIn")}</a>{" "}{t("returnsandRefundPolicy.withYourOrderNumberOur")}</p>
              <p style={{ marginBottom: 0 }}>{t("returnsandRefundPolicy.thisPolicyDoesNotLimit")}{" "}
                <Link to="/terms-and-conditions">{t("footer.terms")}</Link>{" "}{t("returnsandRefundPolicy.and")}{" "}<Link to="/shipping-policy">{t("footer.shipping")}</Link>.
                              </p>
            </Section>
          </main>
        </div>
      </div>

      <style>{`
        .rrp-nav-link:hover { background: rgba(14, 92, 92, 0.07) !important; color: ${green} !important; }
        @media (max-width: 768px) {
          .rrp-layout { flex-direction: column !important; }
          .rrp-nav { width: 100% !important; position: static !important; }
          /* Nine stacked links would push the policy a full screen down on a phone. */
          .rrp-nav-list { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 0 !important; }
          .rrp-nav-link { width: auto !important; padding: 6px 10px !important; font-size: 0.78rem !important; background: #f1f5f9 !important; }
          .rrp-nav-link svg, .rrp-nav-help { display: none; }
        }
      `}</style>
    </div>
  );
};

export default ReturnsandRefundPolicy;
