import React, { useState, useEffect, useCallback } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import { Building2, ShieldCheck, Landmark, UploadCloud, FileText, CheckSquare, ChevronLeft, ChevronRight, Check, AlertTriangle, Upload, Info, ExternalLink, X, Clock, BadgeCheck, BadgeX } from "lucide-react";
import api from "../api/axios";
import { useAuth } from "../context/AuthContext";
import "../styles/sellerRegistration.css";

const STEPS = [
  { id: 1, label: "Business", icon: Building2 },
  { id: 2, label: "Tax & ID", icon: ShieldCheck },
  { id: 3, label: "Bank", icon: Landmark },
  { id: 4, label: "Documents", icon: UploadCloud },
  { id: 5, label: "Compliance", icon: CheckSquare },
  { id: 6, label: "Review", icon: FileText },
];

const BUSINESS_TYPES = ["Sole Proprietorship", "Partnership", "Limited Liability Partnership (LLP)", "Private Limited Company", "Public Limited Company", "One Person Company", "Other"];

const DOCUMENT_TYPES = [
  { type: "GST_CERTIFICATE", name: "GST Certificate", required: true, info: "Issued by GST Department. Required for all registered businesses." },
  { type: "PAN_CARD", name: "PAN Card", required: true, info: "Permanent Account Number card issued by Income Tax Department." },
  { type: "BUSINESS_REGISTRATION", name: "Business Registration Certificate", required: true, info: "Certificate of Incorporation, Partnership Deed, or Proprietorship declaration." },
  { type: "FSSAI_LICENSE", name: "FSSAI License", required: false, info: "Mandatory for food products. Issued by FSSAI." },
  { type: "BIS_ISI_CERTIFICATION", name: "BIS/ISI Certification", required: false, info: "Mandatory for electronics and appliances." },
  { type: "DRUG_LICENSE", name: "Drug License", required: false, info: "Required for pharmaceuticals, cosmetics, health supplements." },
  { type: "TRADEMARK", name: "Trademark Certificate", required: false, info: "For branded products to protect intellectual property." },
  { type: "IEC_CODE", name: "Import/Export Code", required: false, info: "Required if importing goods for resale." },
  { type: "MSME_CERTIFICATE", name: "MSME/Udyam Certificate", required: false, info: "For small & medium enterprises. Optional but provides benefits." },
  { type: "ADHAAR_VERIFICATION", name: "Aadhaar Card", required: false, info: "For individual sellers / proprietorship KYC verification." },
];

const AGREEMENTS = [
  { id: "terms", label: "Seller Terms & Conditions", text: "I have read and agree to the Seller Terms & Conditions governing the marketplace." },
  { id: "compliance", label: "Marketplace Compliance Agreement", text: "I agree to comply with all marketplace rules, policies, and compliance requirements." },
  { id: "refund", label: "Return & Refund Policy", text: "I accept the Return & Refund Policy and will process returns/refunds as per the defined timelines." },
  { id: "privacy", label: "Data Privacy & Consumer Protection", text: "I agree to handle customer data responsibly and comply with Consumer Protection Act 2019 and IT Act 2000." },
];

const SellerRegistration = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [step, setStep] = useState(0);
  const [maxStep, setMaxStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [status, setStatus] = useState("NOT_STARTED");
  const [error, setError] = useState("");
  const [form, setForm] = useState({
    businessName: "", contactPerson: "", businessEmail: "", businessPhone: "",
    businessAddress: "", city: "", state: "", pincode: "", businessType: "Sole Proprietorship",
    website: "", productCategories: "", socialMediaLinks: "",
    gstin: "", panNumber: "", aadhaarNumber: "",
    bankAccountName: "", bankAccountNumber: "", bankIfsc: "", bankName: "", bankBranch: "",
  });
  const [documents, setDocuments] = useState({});
  const [agreements, setAgreements] = useState({});
  const [compliance, setCompliance] = useState([]);
  const [regData, setRegData] = useState(null);
  const [verifyingGstin, setVerifyingGstin] = useState(false);
  const [verifyingBank, setVerifyingBank] = useState(false);
  const [gstinResult, setGstinResult] = useState(null);
  const [bankResult, setBankResult] = useState(null);

  useEffect(() => {
    api.get("/api/seller-registration/status").then((r) => {
      if (r.data.registered) {
        setRegData(r.data.registration);
        setStep(r.data.step || 1);
        setMaxStep(r.data.step || 1);
        setStatus(r.data.status);
        if (r.data.registration) {
          const reg = r.data.registration;
          setForm((prev) => ({
            ...prev,
            businessName: reg.businessName || prev.businessName,
            contactPerson: reg.contactPerson || prev.contactPerson,
            businessEmail: reg.businessEmail || prev.businessEmail,
            businessPhone: reg.businessPhone || prev.businessPhone,
            businessAddress: reg.businessAddress || prev.businessAddress,
            city: reg.city || prev.city,
            state: reg.state || prev.state,
            pincode: reg.pincode || prev.pincode,
            businessType: reg.businessType || prev.businessType,
            website: reg.website || prev.website,
            productCategories: reg.productCategories || prev.productCategories,
            socialMediaLinks: reg.socialMediaLinks || prev.socialMediaLinks,
            gstin: reg.gstin || prev.gstin,
            panNumber: reg.panNumber || prev.panNumber,
            aadhaarNumber: reg.aadhaarNumber || prev.aadhaarNumber,
            bankAccountName: reg.bankAccountName || prev.bankAccountName,
            bankAccountNumber: reg.bankAccountNumber || prev.bankAccountNumber,
            bankIfsc: reg.bankIfsc || prev.bankIfsc,
            bankName: reg.bankName || prev.bankName,
            bankBranch: reg.bankBranch || prev.bankBranch,
          }));
        }
        if (r.data.documents) {
          const docMap = {};
          r.data.documents.forEach((d) => { docMap[d.documentType] = d; });
          setDocuments(docMap);
        }
        if (r.data.compliance) setCompliance(r.data.compliance);
      } else {
        api.post("/api/seller-registration/start").then((s) => {
          setRegData(s.data.registration);
          setStep(s.data.step || 1);
          setMaxStep(s.data.step || 1);
          setStatus(s.data.status);
          if (s.data.compliance) setCompliance(s.data.compliance);
        }).catch(() => {});
      }
    }).catch(() => {}).finally(() => setLoading(false));
  }, []);

  const handleChange = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const saveCurrentStep = useCallback(async (nextStep) => {
    setError("");
    const payload = { ...form, onboardingStep: step };
    const keys = Object.keys(form);
    for (const k of keys) { if (payload[k] === undefined) payload[k] = ""; }
    try {
      await api.post("/api/seller-registration/step", payload);
      setStep(nextStep);
      if (nextStep > maxStep) setMaxStep(nextStep);
    } catch (err) { setError(err.response?.data?.error || "Failed to save progress"); throw err; }
  }, [form, step, maxStep]);

  const handleNext = async () => {
    if (step === 1) {
      if (!form.businessName || !form.contactPerson || !form.businessEmail || !form.businessPhone) {
        setError("Please fill in all required business fields."); return;
      }
    }
    if (step === 5) {
      const allChecked = AGREEMENTS.every((a) => agreements[a.id]);
      if (!allChecked) { setError("Please accept all agreements to proceed."); return; }
    }
    setError("");
    if (step === 6) {
      setSubmitting(true);
      try {
        await api.post("/api/seller-registration/submit");
        setStatus("SUBMITTED");
      } catch (err) { setError(err.response?.data?.error || "Failed to submit registration"); }
      finally { setSubmitting(false); }
      return;
    }
    await saveCurrentStep(step + 1);
  };

  const handlePrev = () => { if (step > 1) setStep(step - 1); };

  const handleDocUpload = async (docType, docName) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".pdf,.jpg,.jpeg,.png";
    input.onchange = async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      const data = new FormData();
      data.append("file", file);
      data.append("upload_preset", "ml_default");
      try {
        const cloudRes = await fetch("https://api.cloudinary.com/v1_1/demo/image/upload", { method: "POST", body: data });
        const cloudData = await cloudRes.json();
        const fileUrl = cloudData.secure_url || URL.createObjectURL(file);
        await api.post("/api/seller-registration/document", {
          documentType: docType,
          documentName: docName,
          fileUrl: fileUrl,
          fileOriginalName: file.name,
          fileSize: file.size,
          mimeType: file.type,
        });
        const statusRes = await api.get("/api/seller-registration/status");
        if (statusRes.data.documents) {
          const docMap = {};
          statusRes.data.documents.forEach((d) => { docMap[d.documentType] = d; });
          setDocuments(docMap);
        }
        if (statusRes.data.compliance) setCompliance(statusRes.data.compliance);
      } catch { setError("Failed to upload document. Please try again."); }
    };
    input.click();
  };

  const handleVerifyGstin = async () => {
    if (!form.gstin || form.gstin.trim().length < 15) {
      setError("Enter a valid 15-character GSTIN before verifying."); return;
    }
    setError("");
    setVerifyingGstin(true);
    setGstinResult(null);
    try {
      const res = await api.post("/api/seller-registration/verify/gstin", { gstin: form.gstin.trim() });
      setGstinResult(res.data);
      setRegData((prev) => ({ ...(prev || {}), gstinStatus: res.data.gstinStatus, gstinLegalName: res.data.legalName, gstinStateCode: res.data.stateCode }));
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || "GSTIN verification failed");
    } finally {
      setVerifyingGstin(false);
    }
  };

  const handleVerifyBank = async () => {
    if (!form.bankAccountNumber || !form.bankIfsc) {
      setError("Enter your bank account number and IFSC before verifying."); return;
    }
    setError("");
    setVerifyingBank(true);
    setBankResult(null);
    try {
      const res = await api.post("/api/seller-registration/verify/bank", {
        accountNumber: form.bankAccountNumber,
        ifsc: form.bankIfsc,
        accountName: form.bankAccountName,
      });
      setBankResult(res.data);
      setRegData((prev) => ({ ...(prev || {}), bankStatus: res.data.bankStatus }));
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || "Bank verification failed");
    } finally {
      setVerifyingBank(false);
    }
  };

  const regGstinStatus = regData?.gstinStatus;
  const regBankStatus = regData?.bankStatus;

  if (loading) {
    return <div className="seller-reg-page" style={{ textAlign: "center", paddingTop: "4rem", color: "#94a3b8" }}>Loading your registration...</div>;
  }

  if (!isAuthenticated) {
    return (
      <div className="seller-reg-page">
        <Helmet><title>Seller Registration | Cauvery Store</title></Helmet>
        <div className="reg-success">
          <div className="reg-success-icon"><ShieldCheck size={28} color="#2563eb" /></div>
          <h2>Log in to start selling</h2>
          <p>You need a Cauvery Store account to complete seller registration. Already have one? Log in and continue your application.</p>
          <div style={{ marginTop: "1.5rem", display: "flex", gap: "0.75rem", justifyContent: "center", flexWrap: "wrap" }}>
            <button className="reg-btn reg-btn-primary" onClick={() => navigate("/login?redirect=/seller/register")}>Log In</button>
            <button className="reg-btn reg-btn-outline" onClick={() => navigate("/register")}>Create Account</button>
          </div>
        </div>
      </div>
    );
  }

  if (status === "SUBMITTED" || status === "APPROVED" || status === "REJECTED") {
    return (
      <div className="seller-reg-page">
        <Helmet><title>Registration Status | Seller | Cauvery Store</title></Helmet>
        <div className="reg-success">
          <div className="reg-success-icon">
            {status === "APPROVED" ? <Check size={28} color="#16a34a" /> : status === "REJECTED" ? <X size={28} color="#dc2626" /> : <Clock size={28} color="#2563eb" />}
          </div>
          <h2>{status === "APPROVED" ? "Registration Approved!" : status === "REJECTED" ? "Registration Rejected" : "Registration Submitted!"}</h2>
          {status === "SUBMITTED" && (
            <>
              <p>Thank you for submitting your seller registration. Our team will review your application and documents. You will be notified via email once approved.</p>
              <div style={{ display: "flex", gap: "0.75rem", justifyContent: "center", flexWrap: "wrap", marginTop: "0.75rem" }}>
                {[{ label: "GSTIN", value: regData?.gstinStatus || "—" }, { label: "Bank", value: regData?.bankStatus || "—" }].map((it) => (
                  <span key={it.label} className={`reg-status-badge ${(it.value === "VERIFIED" ? "approved" : it.value === "FAILED" ? "rejected" : "").toLowerCase()}`} style={{ display: "flex", alignItems: "center", gap: "0.35rem" }}>
                    {it.label}: {it.value}
                  </span>
                ))}
              </div>
              {regData && ((regData.gstinStatus && regData.gstinStatus !== "VERIFIED") || (regData.bankStatus && regData.bankStatus !== "VERIFIED")) && (
                <p style={{ fontSize: "0.8rem", color: "#f59e0b", marginTop: "0.75rem" }}>
                  Note: GSTIN and/or bank verification is still pending. Please complete it so our team can process your approval faster.
                </p>
              )}
            </>
          )}
          {status === "APPROVED" && <p>Congratulations! Your seller account is active. You can now start listing products and selling on Cauvery Store.</p>}
          {status === "REJECTED" && (
            <>
              <p>Your registration has been rejected. Reason: {regData?.rejectionReason || "Please contact support for more details."}</p>
            </>
          )}
          <span className={`reg-status-badge ${status.toLowerCase()}`}>{status}</span>
          <div style={{ marginTop: "1.5rem", display: "flex", gap: "0.75rem", justifyContent: "center", flexWrap: "wrap" }}>
            {status === "APPROVED" && <button className="reg-btn reg-btn-success" onClick={() => navigate("/seller/dashboard")}>Go to Dashboard</button>}
            <button className="reg-btn reg-btn-outline" onClick={() => navigate("/seller/login")}>Seller Login</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="seller-reg-page">
      <Helmet><title>Seller Registration | Cauvery Store</title></Helmet>

      <div className="seller-reg-header">
        <h1>Become a Seller on Cauvery Store</h1>
        <p>Complete your registration in {STEPS.length} simple steps. Start selling to millions of customers across India.</p>
      </div>

      <div className="seller-reg-steps">
        {STEPS.map((s, idx) => {
          const stepNum = idx + 1;
          const isCompleted = stepNum < step;
          const isActive = stepNum === step;
          const isFuture = stepNum > step && stepNum > maxStep;
          return (
            <div key={s.id} className={`seller-reg-step${isCompleted ? " completed" : ""}${isActive ? " active" : ""}`}>
              <div className="reg-step-number">{isCompleted ? <Check size={14} /> : stepNum}</div>
              <span className="reg-step-label">{s.label}</span>
            </div>
          );
        })}
      </div>

      {error && <div style={{ background: "#fef2f2", border: "1px solid #fecaca", borderRadius: "8px", padding: "0.75rem 1rem", fontSize: "0.85rem", color: "#991b1b", marginBottom: "1rem", display: "flex", alignItems: "center", gap: "0.5rem" }}><AlertTriangle size={16} />{error}</div>}

      <div className="reg-form-card">
        {step === 1 && (
          <>
            <h2>Business Profile</h2>
            <p className="reg-form-subtitle">Tell us about your business so we can set up your seller account.</p>
            <div className="reg-form-grid">
              <div className="reg-field full"><label>Business Name <span className="required">*</span></label><input value={form.businessName} onChange={handleChange("businessName")} placeholder="Your registered business name" /></div>
              <div className="reg-field"><label>Contact Person <span className="required">*</span></label><input value={form.contactPerson} onChange={handleChange("contactPerson")} /></div>
              <div className="reg-field"><label>Business Type <span className="required">*</span></label><select value={form.businessType} onChange={handleChange("businessType")}>{BUSINESS_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}</select></div>
              <div className="reg-field"><label>Business Email <span className="required">*</span></label><input type="email" value={form.businessEmail} onChange={handleChange("businessEmail")} /></div>
              <div className="reg-field"><label>Business Phone <span className="required">*</span></label><input value={form.businessPhone} onChange={handleChange("businessPhone")} placeholder="+91 98765 43210" /></div>
              <div className="reg-field full"><label>Business Address <span className="required">*</span></label><textarea value={form.businessAddress} onChange={handleChange("businessAddress")} rows={3} /></div>
              <div className="reg-field"><label>City <span className="required">*</span></label><input value={form.city} onChange={handleChange("city")} /></div>
              <div className="reg-field"><label>State <span className="required">*</span></label><input value={form.state} onChange={handleChange("state")} /></div>
              <div className="reg-field"><label>Pincode <span className="required">*</span></label><input value={form.pincode} onChange={handleChange("pincode")} /></div>
              <div className="reg-field"><label>Website</label><input value={form.website} onChange={handleChange("website")} placeholder="https://" /></div>
              <div className="reg-field"><label>Product Categories</label><input value={form.productCategories} onChange={handleChange("productCategories")} placeholder="e.g. Electronics, Fashion, Home" /></div>
              <div className="reg-field full"><label>Social Media Links</label><input value={form.socialMediaLinks} onChange={handleChange("socialMediaLinks")} placeholder="Instagram, Facebook URLs" /></div>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h2>Tax & Identity Information</h2>
            <p className="reg-form-subtitle">Provide your tax details and identity verification documents for KYC compliance.</p>
            <div className="reg-form-grid">
              <div className="reg-field">
                <label>GSTIN <span className="required">*</span>
                  <span className="reg-tooltip"><Info size={12} /><span className="reg-tooltip-text">Goods & Services Tax Identification Number. Required for taxable turnover above the threshold. Format: 22AAAAA0000A1Z5</span></span>
                </label>
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <input value={form.gstin} onChange={handleChange("gstin")} placeholder="22AAAAA0000A1Z5" maxLength={15} style={{ flex: 1 }} />
                  <button type="button" className="reg-btn reg-btn-primary" style={{ whiteSpace: "nowrap", padding: "0.5rem 0.9rem", fontSize: "0.8rem" }} onClick={handleVerifyGstin} disabled={verifyingGstin}>
                    {verifyingGstin ? "Verifying..." : "Verify GSTIN"}
                  </button>
                </div>
                {regGstinStatus && !gstinResult && (
                  <div style={{ fontSize: "0.78rem", marginTop: "0.4rem", display: "flex", alignItems: "center", gap: "0.35rem", color: regGstinStatus === "VERIFIED" ? "#16a34a" : "#dc2626" }}>
                    {regGstinStatus === "VERIFIED" ? <BadgeCheck size={13} /> : <BadgeX size={13} />}
                    GSTIN {regGstinStatus === "VERIFIED" ? "verified" : "verification " + regGstinStatus.toLowerCase()}
                    {regData?.gstinLegalName && <span style={{ color: "#64748b" }}>· {regData.gstinLegalName}</span>}
                  </div>
                )}
                {gstinResult && (
                  <div style={{ fontSize: "0.78rem", marginTop: "0.4rem", padding: "0.5rem 0.6rem", borderRadius: "8px", background: gstinResult.gstinStatus === "VERIFIED" ? "#f0fdf4" : "#fef2f2", border: `1px solid ${gstinResult.gstinStatus === "VERIFIED" ? "#bbf7d0" : "#fecaca"}`, color: gstinResult.gstinStatus === "VERIFIED" ? "#166534" : "#991b1b" }}>
                    <strong>{gstinResult.gstinStatus === "VERIFIED" ? "GSTIN verified successfully" : "GSTIN verification failed"}</strong>
                    {gstinResult.gstinStatus === "VERIFIED" && (
                      <div style={{ marginTop: "0.25rem" }}>
                        {gstinResult.legalName && <div>Legal name: {gstinResult.legalName}</div>}
                        {gstinResult.stateCode && <div>State code: {gstinResult.stateCode}</div>}
                        {gstinResult.verificationRef && <div style={{ color: "#64748b" }}>Ref: {gstinResult.verificationRef}</div>}
                      </div>
                    )}
                  </div>
                )}
              </div>
              <div className="reg-field">
                <label>PAN Number <span className="required">*</span>
                  <span className="reg-tooltip"><Info size={12} /><span className="reg-tooltip-text">Permanent Account Number. Mandatory for tax compliance and KYC. Format: AAAAA9999A</span></span>
                </label>
                <input value={form.panNumber} onChange={handleChange("panNumber")} placeholder="AAAAA9999A" maxLength={10} style={{ textTransform: "uppercase" }} />
              </div>
              <div className="reg-field">
                <label>Aadhaar Number
                  <span className="reg-tooltip"><Info size={12} /><span className="reg-tooltip-text">12-digit Aadhaar number for individual sellers and proprietorships. Used for KYC.</span></span>
                </label>
                <input value={form.aadhaarNumber} onChange={handleChange("aadhaarNumber")} placeholder="1234 5678 9012" maxLength={14} />
              </div>
            </div>
          </>
        )}

        {step === 3 && (
          <>
            <h2>Bank Account Details</h2>
            <p className="reg-form-subtitle">Enter your bank account information for payouts and settlements.</p>
            <div className="reg-form-grid">
              <div className="reg-field full"><label>Account Holder Name <span className="required">*</span></label><input value={form.bankAccountName} onChange={handleChange("bankAccountName")} placeholder="As per bank records" /></div>
              <div className="reg-field"><label>Account Number <span className="required">*</span></label><input value={form.bankAccountNumber} onChange={handleChange("bankAccountNumber")} type="password" placeholder="Enter account number" /></div>
              <div className="reg-field"><label>Confirm Account Number <span className="required">*</span></label><input onChange={(e) => { if (e.target.value !== form.bankAccountNumber) setError("Account numbers do not match"); else setError(""); }} type="password" placeholder="Re-enter account number" /></div>
              <div className="reg-field"><label>IFSC Code <span className="required">*</span></label><input value={form.bankIfsc} onChange={handleChange("bankIfsc")} placeholder="SBIN0001234" maxLength={11} style={{ textTransform: "uppercase" }} /></div>
              <div className="reg-field"><label>Bank Name <span className="required">*</span></label><input value={form.bankName} onChange={handleChange("bankName")} /></div>
              <div className="reg-field"><label>Branch <span className="required">*</span></label><input value={form.bankBranch} onChange={handleChange("bankBranch")} /></div>
              <div className="reg-field full">
                <button type="button" className="reg-btn reg-btn-primary" style={{ width: "100%", justifyContent: "center" }} onClick={handleVerifyBank} disabled={verifyingBank}>
                  {verifyingBank ? "Verifying bank account..." : "Verify Bank Account"}
                </button>
                {regBankStatus && !bankResult && (
                  <div style={{ fontSize: "0.78rem", marginTop: "0.4rem", display: "flex", alignItems: "center", gap: "0.35rem", color: regBankStatus === "VERIFIED" ? "#16a34a" : "#dc2626" }}>
                    {regBankStatus === "VERIFIED" ? <BadgeCheck size={13} /> : <BadgeX size={13} />}
                    Bank account {regBankStatus === "VERIFIED" ? "verified" : "verification " + regBankStatus.toLowerCase()}
                  </div>
                )}
                {bankResult && (
                  <div style={{ fontSize: "0.78rem", marginTop: "0.4rem", padding: "0.5rem 0.6rem", borderRadius: "8px", background: bankResult.bankStatus === "VERIFIED" ? "#f0fdf4" : "#fef2f2", border: `1px solid ${bankResult.bankStatus === "VERIFIED" ? "#bbf7d0" : "#fecaca"}`, color: bankResult.bankStatus === "VERIFIED" ? "#166534" : "#991b1b" }}>
                    <strong>{bankResult.bankStatus === "VERIFIED" ? "Bank account verified successfully" : "Bank account verification failed"}</strong>
                    {bankResult.bankStatus === "VERIFIED" && bankResult.reference && <div style={{ marginTop: "0.25rem", color: "#64748b" }}>Ref: {bankResult.reference}</div>}
                  </div>
                )}
              </div>
            </div>
          </>
        )}

        {step === 4 && (
          <>
            <h2>Document Upload</h2>
            <p className="reg-form-subtitle">Upload required documents for verification. Supported formats: PDF, JPG, PNG (max 10MB each).</p>
            <div className="reg-doc-grid">
              {DOCUMENT_TYPES.map((dt) => {
                const doc = documents[dt.type];
                const isUploaded = !!doc;
                const isVerified = doc?.status === "VERIFIED";
                const isRejected = doc?.status === "REJECTED";
                return (
                  <div key={dt.type} className={`reg-doc-card${isVerified ? " uploaded" : ""}${isRejected ? " rejected" : ""}`}>
                    <div className="reg-doc-icon">{isVerified ? <Check size={24} /> : <Upload size={24} />}</div>
                    <h4>{dt.name}</h4>
                    <p>
                      {dt.required ? "Required" : "Optional"}
                      <span className="reg-tooltip" style={{ marginLeft: "4px" }}>
                        <Info size={11} />
                        <span className="reg-tooltip-text">{dt.info}</span>
                      </span>
                    </p>
                    {isUploaded && (
                      <span className={`reg-doc-status ${doc.status.toLowerCase()}`}>
                        {doc.status === "VERIFIED" ? "Verified" : doc.status === "REJECTED" ? "Rejected" : "Pending"}
                      </span>
                    )}
                    {isRejected && doc.rejectionReason && <p style={{ fontSize: "0.72rem", color: "#dc2626", marginTop: "4px" }}>{doc.rejectionReason}</p>}
                    <button className="reg-btn reg-btn-primary" style={{ marginTop: "0.75rem", padding: "0.4rem 1rem", fontSize: "0.8rem" }} onClick={() => handleDocUpload(dt.type, dt.name)}>
                      {isUploaded ? "Re-upload" : "Upload"}
                    </button>
                  </div>
                );
              })}
            </div>
          </>
        )}

        {step === 5 && (
          <>
            <h2>Compliance & Agreements</h2>
            <p className="reg-form-subtitle">Complete the compliance checklist and accept the legal agreements to proceed.</p>

            <h3 style={{ fontSize: "0.95rem", fontWeight: 600, color: "#0f172a", margin: "0 0 0.75rem" }}>Compliance Requirements</h3>
            <div className="reg-compliance-list">
              {compliance.map((c) => (
                <div key={c.id || c.requirementType} className={`reg-compliance-item${c.isCompleted ? " completed" : ""}`}>
                  <div className={`reg-compliance-icon${c.isCompleted ? " completed" : " pending"}${c.isMandatory ? " mandatory" : ""}`}>
                    {c.isCompleted ? <Check size={12} /> : <AlertTriangle size={12} />}
                  </div>
                  <div className="reg-compliance-info">
                    <h4>{c.requirementName}</h4>
                    <p>{c.description}</p>
                  </div>
                  {c.isMandatory && <span style={{ fontSize: "0.7rem", color: "#f59e0b", fontWeight: 600, flexShrink: 0 }}>MANDATORY</span>}
                  {c.isCompleted && <Check size={16} color="#16a34a" style={{ flexShrink: 0 }} />}
                </div>
              ))}
            </div>

            <h3 style={{ fontSize: "0.95rem", fontWeight: 600, color: "#0f172a", margin: "1.5rem 0 0.75rem" }}>Legal Agreements</h3>
            <div className="reg-agreements">
              {AGREEMENTS.map((a) => (
                <div key={a.id} className="reg-agreement-check" onClick={() => setAgreements((prev) => ({ ...prev, [a.id]: !prev[a.id] }))}>
                  <input type="checkbox" checked={!!agreements[a.id]} onChange={() => {}} />
                  <label><strong>{a.label}:</strong> {a.text}</label>
                </div>
              ))}
            </div>
          </>
        )}

        {step === 6 && (
          <>
            <h2>Review & Submit</h2>
            <p className="reg-form-subtitle">Please review your information before submitting. You can go back to edit any section.</p>
            <div style={{ marginBottom: "1.5rem" }}>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, color: "#0f172a", marginBottom: "0.5rem" }}>Business Profile</h3>
              <div className="reg-review-grid">
                <div className="reg-review-item"><p className="reg-review-label">Business Name</p><p className="reg-review-value">{form.businessName || "-"}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">Business Type</p><p className="reg-review-value">{form.businessType}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">Contact Person</p><p className="reg-review-value">{form.contactPerson || "-"}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">Email / Phone</p><p className="reg-review-value">{form.businessEmail} / {form.businessPhone}</p></div>
                <div className="reg-review-item full"><p className="reg-review-label">Address</p><p className="reg-review-value">{form.businessAddress}, {form.city}, {form.state} - {form.pincode}</p></div>
              </div>
            </div>
            <div style={{ marginBottom: "1.5rem" }}>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, color: "#0f172a", marginBottom: "0.5rem" }}>Tax & Identity</h3>
              <div className="reg-review-grid">
                <div className="reg-review-item"><p className="reg-review-label">GSTIN</p><p className="reg-review-value">{form.gstin || "-"}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">PAN</p><p className="reg-review-value">{form.panNumber || "-"}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">Aadhaar</p><p className="reg-review-value">{form.aadhaarNumber || "-"}</p></div>
              </div>
            </div>
            <div style={{ marginBottom: "1.5rem" }}>
              <h3 style={{ fontSize: "0.95rem", fontWeight: 600, color: "#0f172a", marginBottom: "0.5rem" }}>Bank Details</h3>
              <div className="reg-review-grid">
                <div className="reg-review-item"><p className="reg-review-label">Account Holder</p><p className="reg-review-value">{form.bankAccountName || "-"}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">Bank / IFSC</p><p className="reg-review-value">{form.bankName} / {form.bankIfsc}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">Account No.</p><p className="reg-review-value">{"*".repeat(Math.max(0, (form.bankAccountNumber || "").length - 4)) + (form.bankAccountNumber || "").slice(-4)}</p></div>
                <div className="reg-review-item"><p className="reg-review-label">Branch</p><p className="reg-review-value">{form.bankBranch || "-"}</p></div>
              </div>
            </div>
          </>
        )}
      </div>

      <div className="reg-actions">
        <button className="reg-btn reg-btn-secondary" onClick={handlePrev} disabled={step <= 1}><ChevronLeft size={16} /> Back</button>
        {step < 6 ? (
          <button className="reg-btn reg-btn-primary" onClick={handleNext}>Next <ChevronRight size={16} /></button>
        ) : (
          <button className="reg-btn reg-btn-success" onClick={handleNext} disabled={submitting}>
            {submitting ? "Submitting..." : <><Check size={16} /> Submit Registration</>}
          </button>
        )}
      </div>
    </div>
  );
};
export default SellerRegistration;
