import React, { useState, useEffect } from "react";
import api from "../api/axios";
import { useConfirm, showMessage } from "../components/ConfirmDialog";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";

const PaymentMethods = () => {
  const { t } = useTranslation();

  const confirm = useConfirm();
  const [methods, setMethods] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ type:"credit_card", maskedNumber:"", expiry:"", cardholderName:"", upiId:"", bankName:"", isDefault:false });
  const fetch = async () => { try { setMethods((await api.get("/api/payment-methods")).data); } catch {} };
  useEffect(() => { fetch(); }, []);
  const handleSubmit = async (e) => {
    e.preventDefault();
    try { await api.post("/api/payment-methods", form); setShowForm(false); setForm({ type:"credit_card", maskedNumber:"", expiry:"", cardholderName:"", upiId:"", bankName:"", isDefault:false }); fetch(); } catch { showMessage("Failed"); }
  };
  const handleDelete = async (id) => { if (!(await confirm({ title: t("paymentMethods.removeThisPaymentMethod"), message: t("paymentMethods.youCanAddItAgain"), confirmLabel: i18n.t("msgs.remove"), cancelLabel: i18n.t("msgs.keepIt"), danger: true }))) return; try { await api.delete(`/api/payment-methods/${id}`); fetch(); } catch { showMessage("Failed"); } };
  const handleSetDefault = async (id) => { try { await api.put(`/api/payment-methods/${id}/default`); fetch(); } catch { showMessage("Failed"); } };
  const inp = { padding:"0.5rem 0.6rem", border:"1px solid #d1d5db", borderRadius:6, fontSize:"0.85rem", width:"100%", boxSizing:"border-box" };
  return (
    <div style={{ padding:"1.5rem", maxWidth:"800px", margin:"0 auto" }}>
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:"1.5rem" }}>
        <h1 style={{ fontSize:"1.5rem", fontWeight:700, margin:0 }}>{t("paymentMethods.paymentMethods")}</h1>
        <button onClick={() => setShowForm(!showForm)} style={{ padding:"0.5rem 1rem", background:"#0E5C5C", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{showForm ? t("addressBook.cancel") : t("paymentMethods.addMethod")}</button>
      </div>
      {showForm && (
        <div style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1.5rem", marginBottom:"1.5rem" }}>
          <h3 style={{ fontSize:"1.1rem", fontWeight:600, marginBottom:"1rem" }}>{t("paymentMethods.newPaymentMethod")}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display:"grid", gap:"1rem", marginBottom:"1rem" }}>
              <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>{t("paymentMethods.type")}</label>
                <select value={form.type} onChange={e => setForm({...form,type:e.target.value})} style={inp}>
                  <option value="credit_card">{t("paymentMethods.creditCard")}</option>
                  <option value="debit_card">{t("paymentMethods.debitCard")}</option>
                  <option value="upi">UPI</option>
                  <option value="net_banking">{t("footer.netBanking")}</option>
                </select>
              </div>
              {(form.type === "credit_card" || form.type === "debit_card") && <>
                <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>{t("paymentMethods.cardNumberMasked")}</label><input value={form.maskedNumber} onChange={e => setForm({...form,maskedNumber:e.target.value})} style={inp} placeholder={t("paymentMethods.xxxxXxxxXxxx1234")} /></div>
                <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"1rem" }}>
                  <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>{t("paymentMethods.expiry")}</label><input value={form.expiry} onChange={e => setForm({...form,expiry:e.target.value})} style={inp} placeholder={t("paymentMethods.mmYy")} /></div>
                  <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>{t("paymentMethods.cardholderName")}</label><input value={form.cardholderName} onChange={e => setForm({...form,cardholderName:e.target.value})} style={inp} /></div>
                </div>
              </>}
              {form.type === "upi" && <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>{t("paymentMethods.upiId")}</label><input value={form.upiId} onChange={e => setForm({...form,upiId:e.target.value})} style={inp} placeholder={t("paymentMethods.userPaytm")} /></div>}
              {form.type === "net_banking" && <div><label style={{ fontSize:"0.85rem", fontWeight:500, display:"block", marginBottom:"4px" }}>{t("paymentMethods.bankName")}</label><input value={form.bankName} onChange={e => setForm({...form,bankName:e.target.value})} style={inp} /></div>}
            </div>
            <label style={{ fontSize:"0.85rem", display:"flex", alignItems:"center", gap:"6px" }}><input type="checkbox" checked={form.isDefault} onChange={e => setForm({...form,isDefault:e.target.checked})} />{" "}{t("paymentMethods.setAsDefault")}</label>
            <button type="submit" style={{ marginTop:"1rem", padding:"0.5rem 1.5rem", background:"#0E5C5C", color:"#fff", border:"none", borderRadius:6, cursor:"pointer", fontSize:"0.85rem" }}>{t("addressBook.save")}</button>
          </form>
        </div>
      )}
      <div style={{ display:"grid", gap:"0.75rem" }}>
        {methods.length === 0 && !showForm && <p style={{ color:"#94a3b8", textAlign:"center", padding:"2rem" }}>{t("paymentMethods.noPaymentMethodsSaved")}</p>}
        {methods.map(m => (
          <div key={m.id} style={{ background:"#fff", border:"1px solid #e5e7eb", borderRadius:10, padding:"1rem", display:"flex", justifyContent:"space-between", alignItems:"center" }}>
            <div>
              <div style={{ fontWeight:600, fontSize:"0.9rem" }}>
                {m.type === "upi" ? i18n.t("msgs.upiLabel", { id: m.upiId }) : m.type === "net_banking" ? i18n.t("msgs.netBankingLabel", { bank: m.bankName }) : `${m.cardholderName || i18n.t("msgs.cardWord")} - ${m.maskedNumber || "****"}`}
                {m.isDefault && <span style={{ marginLeft:"0.5rem", padding:"0.15rem 0.5rem", background:"#edf8f7", color:"#0E5C5C", borderRadius:4, fontSize:"0.7rem", fontWeight:600 }}>{t("addressBook.default")}</span>}
              </div>
              {(m.type === "credit_card" || m.type === "debit_card") && <div style={{ fontSize:"0.8rem", color:"#6b7280", marginTop:"0.25rem" }}>{t("paymentMethods.expires")}{" "}{m.expiry || "N/A"}</div>}
            </div>
            <div style={{ display:"flex", gap:"0.5rem" }}>
              {!m.isDefault && <button onClick={() => handleSetDefault(m.id)} style={{ padding:"0.3rem 0.7rem", background:"#3b82f6", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>{t("paymentMethods.setDefault")}</button>}
              <button onClick={() => handleDelete(m.id)} style={{ padding:"0.3rem 0.7rem", background:"#dc2626", color:"#fff", border:"none", borderRadius:4, cursor:"pointer", fontSize:"0.75rem" }}>{t("cart.remove")}</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
export default PaymentMethods;