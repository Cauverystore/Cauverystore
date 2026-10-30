import React, { useState, useEffect, useCallback } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import userService from "../services/userService";
import securityService from "../services/securityService";
import { useAuth } from "../context/AuthContext";
import { getCart, moveToCart } from "../services/cartService";
import { removeFromWishlist } from "../services/wishlistService";
import { useWishlist } from "../context/WishlistContext";
import "../styles/profile.css";
import { useConfirm } from "../components/ConfirmDialog";
import { useTranslation } from "react-i18next";
import i18n from "../i18n";
import useAltText from "../hooks/useAltText";

const PLACEHOLDER = "/images/placeholder.svg";

const TABS = [
  {
    key: "personal",

    get label() {
      return i18n.t("profile.personalInfo");
    }
  },
  {
    key: "addresses",

    get label() {
      return i18n.t("addressBook.addresses");
    }
  },
  {
    key: "payment",

    get label() {
      return i18n.t("paymentMethods.paymentMethods");
    }
  },
  {
    key: "saved",

    get label() {
      return i18n.t("profile.savedItems");
    }
  },
  {
    key: "preferences",

    get label() {
      return i18n.t("profile.preferences");
    }
  },
  {
    key: "orders",

    get label() {
      return i18n.t("nav.ordersShort");
    }
  },
  {
    key: "reviews",

    get label() {
      return i18n.t("profile.reviews");
    }
  },
  {
    key: "security",

    get label() {
      return i18n.t("profile.security");
    }
  },
];

// line1/line2 are the current shape; street is kept because addresses saved before the split
// still carry it, and an edit form that ignored it would blank a line the customer never touched.
const initialAddr = () => ({
  fullName: "", phone: "", line1: "", line2: "", street: "", city: "", state: "", pincode: "",
  country: "India",
  label: "HOME", landmark: "", deliveryInstructions: "", isDefault: false, isBilling: false
});

const Profile = () => {
  const altText = useAltText();
  const { t } = useTranslation();

  const navigate = useNavigate();
  const confirm = useConfirm();
  const { logout } = useAuth();
  const [searchParams] = useSearchParams();
  const [activeTab, setActiveTab] = useState(searchParams.get('tab') || "personal");

  const [profile, setProfile] = useState(null);
  const [addresses, setAddresses] = useState([]);
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [preferences, setPreferences] = useState(null);
  const [orders, setOrders] = useState([]);
  const [reviews, setReviews] = useState([]);
  const [wishlist, setWishlist] = useState([]);
  const [savedForLater, setSavedForLater] = useState([]);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [editingAddr, setEditingAddr] = useState(null);
  const [showAddrForm, setShowAddrForm] = useState(false);

  const [profileForm, setProfileForm] = useState({});

  const [showPaymentForm, setShowPaymentForm] = useState(false);
  const [paymentForm, setPaymentForm] = useState({ type: "CARD", maskedNumber: "", cardholderName: "", expiry: "", upiId: "", bankName: "" });

  const [me, setMe] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [loadingSecurity, setLoadingSecurity] = useState(false);
  const [securityMsg, setSecurityMsg] = useState("");
  const [securityErr, setSecurityErr] = useState("");
  const [twofaSetup, setTwofaSetup] = useState(null);
  const [mfaOtp, setMfaOtp] = useState("");
  const [mfaBusy, setMfaBusy] = useState(false);
  const [pwForm, setPwForm] = useState({ oldPassword: "", newPassword: "", confirmPassword: "" });
  const [sessionBusy, setSessionBusy] = useState(null);

  const { refresh: refreshWishlistCtx } = useWishlist();

  const loadAll = useCallback(async () => {
    try {
      const [pRes, aRes, pmRes, prefRes, oRes, rRes, wRes, cRes] = await Promise.all([
        userService.getProfile().catch(e => { void e; return { data: null }; }),
        userService.getAddresses().catch(e => { void e; return { data: [] }; }),
        userService.getPaymentMethods().catch(e => { void e; return { data: [] }; }),
        userService.getPreferences().catch(e => { void e; return { data: null }; }),
        userService.getOrderHistory().catch(e => { void e; return { data: [] }; }),
        userService.getMyReviews().catch(e => { void e; return { data: [] }; }),
        userService.getWishlist().catch(e => { void e; return { data: [] }; }),
        getCart().catch(e => { void e; return { data: null }; }),
      ]);
      setProfile(pRes.data);
      setProfileForm({ fullName: pRes.data?.fullName || "", email: pRes.data?.email || "", phone: pRes.data?.phone || "" });
      setAddresses(Array.isArray(aRes.data) ? aRes.data : []);
      setPaymentMethods(Array.isArray(pmRes.data) ? pmRes.data : []);
      setPreferences(prefRes.data);
      setOrders(Array.isArray(oRes.data) ? oRes.data : []);
      setReviews(Array.isArray(rRes.data) ? rRes.data : []);
      setWishlist(Array.isArray(wRes.data) ? wRes.data : []);
      setSavedForLater(Array.isArray(cRes.data?.savedForLater) ? cRes.data.savedForLater : []);
    } catch (err) {
      setError(t("profile.failedToLoadProfileData"));
    }
    setLoading(false);
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const loadSecurity = useCallback(async () => {
    setLoadingSecurity(true);
    try {
      const [meRes, sessRes] = await Promise.all([
        securityService.getMe().catch(e => { void e; return { data: null }; }),
        securityService.getSessions().catch(e => { void e; return { data: [] }; }),
      ]);
      setMe(meRes.data);
      setSessions(Array.isArray(sessRes.data) ? sessRes.data : []);
    } catch { /* ignore */ }
    setLoadingSecurity(false);
  }, []);

  useEffect(() => { loadSecurity(); }, [loadSecurity]);

  const notify = (msg, type = "success") => {
    if (type === "error") { setError(msg); setSuccess(""); }
    else { setSuccess(msg); setError(""); }
    setTimeout(() => { setError(""); setSuccess(""); }, 4000);
  };

  const handleRemoveWishlistItem = async (productId) => {
    try {
      await removeFromWishlist(productId);
      setWishlist((prev) => prev.filter((i) => (i.product?.id || i.product?._id || i.productId) !== productId));
      refreshWishlistCtx();
      notify(i18n.t("msgs.removedFromWishlist"));
    } catch (err) { notify(i18n.t("msgs.failedRemoveItem"), "error"); }
  };

  const handleMoveSavedToCart = async (itemId) => {
    try {
      await moveToCart(itemId);
      setSavedForLater((prev) => prev.filter((i) => (i.id || i._id) !== itemId));
      notify(i18n.t("msgs.movedToCart"));
    } catch (err) { notify(i18n.t("msgs.failedMoveToCart"), "error"); }
  };

  const handleProfileSave = async () => {
    setSaving(true); setError(""); setSuccess("");
    try {
      const res = await userService.updateProfile(profileForm);
      setProfile(res.data);
      notify(i18n.t("msgs.profileUpdated"));
    } catch (err) { notify(err.response?.data?.message || i18n.t("msgs.failedUpdateProfile"), "error"); }
    setSaving(false);
  };

  const handleAddAddress = async () => {
    // A legacy address supplies its first line as street, a new one as line1. Requiring line1
    // outright would make an old address unsaveable until it was retyped.
    const firstLine = editingAddr.line1?.trim() || editingAddr.street?.trim();
    if (!editingAddr.fullName?.trim() || !firstLine || !editingAddr.city?.trim() || !editingAddr.state?.trim() || !editingAddr.pincode?.trim()) {
      notify(i18n.t("msgs.fillRequired"), "error"); return;
    }
    setSaving(true);
    try {
      const res = editingAddr.id
        ? await userService.updateAddress(editingAddr.id, editingAddr)
        : await userService.addAddress(editingAddr);
      await loadAll();
      setShowAddrForm(false); setEditingAddr(null);
      notify(editingAddr.id ? i18n.t("msgs.addressUpdated") : i18n.t("msgs.addressAdded"));
    } catch (err) { notify(err.response?.data?.message || i18n.t("msgs.failedSaveAddress"), "error"); }
    setSaving(false);
  };

  const handleDeleteAddress = async (id) => {
    if (!(await confirm({ title: t("profile.deleteThisAddress"), message: t("profile.itWillBeRemovedFrom"), confirmLabel: i18n.t("msgs.delete"), cancelLabel: i18n.t("msgs.keepIt"), danger: true }))) return;
    try {
      await userService.deleteAddress(id);
      setAddresses(addresses.filter(a => (a.id || a._id) !== id));
      notify(i18n.t("msgs.addressDeleted"));
    } catch (err) {
      // The server refuses to delete an address an order still points at, and says why - it is
      // needed for the invoice and for the delivery. Replacing that with "Failed to delete"
      // leaves somebody clicking a button that will never work and no idea what to do instead.
      notify(err.response?.data?.error || err.response?.data?.message || i18n.t("msgs.failedDeleteAddress"), "error");
    }
  };

  const handleAddPayment = async () => {
    if (paymentForm.type === "CARD" && !paymentForm.maskedNumber?.trim()) {
      notify(i18n.t("msgs.cardRequired"), "error"); return;
    }
    if (paymentForm.type === "UPI" && !paymentForm.upiId?.trim()) {
      notify(i18n.t("msgs.upiRequired"), "error"); return;
    }
    setSaving(true);
    try {
      await userService.addPaymentMethod(paymentForm);
      const res = await userService.getPaymentMethods();
      setPaymentMethods(Array.isArray(res.data) ? res.data : []);
      setShowPaymentForm(false);
      setPaymentForm({ type: "CARD", maskedNumber: "", cardholderName: "", expiry: "", upiId: "", bankName: "" });
      notify(i18n.t("msgs.paymentAdded"));
    } catch (err) { notify(i18n.t("msgs.failedAddPayment"), "error"); }
    setSaving(false);
  };

  const handleDeletePayment = async (id) => {
    if (!(await confirm({ title: t("paymentMethods.removeThisPaymentMethod"), message: t("paymentMethods.youCanAddItAgain"), confirmLabel: i18n.t("msgs.remove"), cancelLabel: i18n.t("msgs.keepIt"), danger: true }))) return;
    try {
      await userService.deletePaymentMethod(id);
      setPaymentMethods(paymentMethods.filter(p => (p.id || p._id) !== id));
      notify(i18n.t("msgs.paymentRemoved"));
    } catch (err) { notify(i18n.t("msgs.failedRemovePayment"), "error"); }
  };

  const handlePrefsSave = async (updates) => {
    setSaving(true);
    try {
      const res = await userService.updatePreferences({ ...preferences, ...updates });
      setPreferences(res.data);
      notify(i18n.t("msgs.prefsSaved"));
    } catch (err) { notify(i18n.t("msgs.failedSavePrefs"), "error"); }
    setSaving(false);
  };

  const secNotify = (msg, type = "success") => {
    if (type === "error") { setSecurityErr(msg); setSecurityMsg(""); }
    else { setSecurityMsg(msg); setSecurityErr(""); }
    setTimeout(() => { setSecurityErr(""); setSecurityMsg(""); }, 5000);
  };

  const handleEnable2fa = async () => {
    setMfaBusy(true); setSecurityErr(""); setSecurityMsg("");
    try {
      const res = await securityService.enable2fa();
      setTwofaSetup(res.data);
      setMfaOtp("");
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || i18n.t("msgs.failed2faSetup"), "error"); }
    setMfaBusy(false);
  };

  const handleConfirm2fa = async () => {
    if (!mfaOtp.trim()) { secNotify(i18n.t("msgs.enterAuthCode"), "error"); return; }
    setMfaBusy(true); setSecurityErr("");
    try {
      await securityService.confirm2fa(mfaOtp.trim());
      setTwofaSetup(null); setMfaOtp("");
      secNotify(i18n.t("msgs.mfaEnabled"));
      await loadSecurity();
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || i18n.t("msgs.invalidCode"), "error"); }
    setMfaBusy(false);
  };

  const handleDisable2fa = async () => {
    if (!mfaOtp.trim()) { secNotify(i18n.t("msgs.enterCodeDisable2fa"), "error"); return; }
    setMfaBusy(true); setSecurityErr("");
    try {
      await securityService.disable2fa(mfaOtp.trim());
      setMfaOtp("");
      secNotify(i18n.t("msgs.mfaDisabled"));
      await loadSecurity();
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || i18n.t("msgs.invalidCode"), "error"); }
    setMfaBusy(false);
  };

  const handleChangePassword = async () => {
    if (!pwForm.oldPassword || !pwForm.newPassword) { secNotify(i18n.t("msgs.enterOldNewPassword"), "error"); return; }
    if (pwForm.newPassword.length < 8) { secNotify(i18n.t("msgs.newPasswordMin8"), "error"); return; }
    if (pwForm.newPassword !== pwForm.confirmPassword) { secNotify(i18n.t("msgs.passwordsNoMatch"), "error"); return; }
    setMfaBusy(true); setSecurityErr("");
    try {
      await securityService.changePassword(pwForm.oldPassword, pwForm.newPassword);
      setPwForm({ oldPassword: "", newPassword: "", confirmPassword: "" });
      secNotify(i18n.t("msgs.passwordChanged"));
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || i18n.t("msgs.failedChangePassword"), "error"); }
    setMfaBusy(false);
  };

  const handleRevokeSession = async (sessionId) => {
    if (!(await confirm({ title: t("profile.signOutThisDevice"), message: t("profile.thatDeviceWillNeedTo"), confirmLabel: i18n.t("msgs.signOut") }))) return;
    setSessionBusy(sessionId);
    try {
      await securityService.revokeSession(sessionId);
      await loadSecurity();
      secNotify(i18n.t("msgs.sessionRevoked"));
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || i18n.t("msgs.failedRevokeSession"), "error"); }
    setSessionBusy(null);
  };

  const handleLogoutAll = async () => {
    if (!(await confirm({ title: t("profile.signOutFromAllDevices"), message: t("profile.youWillBeSignedOut"), confirmLabel: i18n.t("msgs.signOutEverywhere") }))) return;
    try {
      await securityService.logoutAll();
    } catch { /* ignore */ }
    await logout();
    navigate("/login");
  };

  if (loading) {
    return <div style={{ textAlign: "center", padding: "3rem", color: "var(--color-text-secondary)" }}>{t("profile.loadingProfile")}</div>;
  }

  const userId = profile?.id || "";
  const userRole = profile?.role || "";
  const userLabel = userRole === "SELLER" ? i18n.t("msgs.sellerId") : userRole === "CUSTOMER" ? i18n.t("msgs.customerId") : i18n.t("msgs.userId");
  const profilePic = profile?.profilePicture || PLACEHOLDER;

  const renderNotification = () => (
    <div style={{ position: "sticky", top: 0, zIndex: 10 }}>
      {error && <div className="profile-notification error">{error}<button onClick={() => setError("")} className="profile-notif-close">&times;</button></div>}
      {success && <div className="profile-notification success">{success}<button onClick={() => setSuccess("")} className="profile-notif-close">&times;</button></div>}
    </div>
  );

  const renderTab = (key) => {
    switch (key) {
      case "personal": return renderPersonal();
      case "addresses": return renderAddresses();
      case "payment": return renderPayment();
      case "saved": return renderSavedItems();
      case "preferences": return renderPreferences();
      case "orders": return renderOrders();
      case "reviews": return renderReviews();
      case "security": return renderSecurity();
      default: return null;
    }
  };

  const renderPersonal = () => (
    <div className="profile-section-card">
      <div className="profile-card-header">
        <h3>{t("profile.personalInformation")}</h3>
        {userId && <span className="profile-customer-id">{userLabel}: {userId.toString().slice(-8).toUpperCase()}</span>}
      </div>
      <div className="profile-avatar-section">
        <img src={profilePic} alt={altText.profilePhoto()} width="80" height="80" className="profile-avatar"
          onError={(e) => { e.target.src = PLACEHOLDER; }} />
        <div>
          <div className="profile-name-display">{profile?.fullName || profile?.name}</div>
          <div className="profile-email-display">{profile?.email}</div>
        </div>
      </div>
      <div className="profile-form-grid">
        <div className="pf-group">
          <label className="pf-label">{t("checkout.fullName")}</label>
          <input className="pf-input" value={profileForm.fullName || ""}
            onChange={(e) => setProfileForm({ ...profileForm, fullName: e.target.value })} />
        </div>
        <div className="pf-group">
          <label className="pf-label">{t("profile.emailAddress")}</label>
          <input className="pf-input" value={profileForm.email || ""}
            onChange={(e) => setProfileForm({ ...profileForm, email: e.target.value })} />
        </div>
        <div className="pf-group">
          <label className="pf-label">{t("checkout.phoneNumber")}</label>
          <input className="pf-input" value={profileForm.phone || ""}
            onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })} />
        </div>
      </div>
      <div className="profile-form-actions">
        <button className="pf-btn pf-btn-primary" onClick={handleProfileSave} disabled={saving}>
          {saving ? t("addressBook.saving") : t("profile.saveChanges")}
        </button>
      </div>
    </div>
  );

  const renderAddresses = () => (
    <div className="profile-section-card">
      <div className="profile-card-header">
        <h3>{t("profile.savedAddresses")}</h3>
        <button className="pf-btn pf-btn-outline" onClick={() => { setEditingAddr(initialAddr()); setShowAddrForm(true); }}>{t("profile.addNew")}</button>
      </div>

      {showAddrForm && (
        <div className="profile-addr-form">
          <h4 style={{ marginBottom: "0.75rem", fontWeight: 600 }}>{editingAddr?.id ? t("addressBook.editAddress") : t("profile.newAddress")}</h4>
          <div className="profile-form-grid two-col">
            <div className="pf-group">
              <label className="pf-label">{t("profile.fullName")}</label>
              <input className="pf-input" value={editingAddr?.fullName || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, fullName: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">{t("profile.phone")}</label>
              <input className="pf-input" value={editingAddr?.phone || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, phone: e.target.value })} />
            </div>
            <div className="pf-group full-width">
              <label className="pf-label">{t("profile.addressLine1")}</label>
              {/* Falls back to street so an address saved before the split shows what it holds
                  rather than an empty box the customer would have to fill in again. */}
              <input className="pf-input" value={editingAddr?.line1 ?? editingAddr?.street ?? ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, line1: e.target.value })}
                placeholder={t("profile.houseFlatNumberAndStreet")} />
            </div>
            <div className="pf-group full-width">
              <label className="pf-label">{t("profile.addressLine2")}</label>
              <input className="pf-input" value={editingAddr?.line2 || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, line2: e.target.value })}
                placeholder={t("profile.areaLocalityOptional")} />
            </div>
            <div className="pf-group">
              <label className="pf-label">{t("profile.city")}</label>
              <input className="pf-input" value={editingAddr?.city || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, city: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">{t("profile.state")}</label>
              <input className="pf-input" value={editingAddr?.state || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, state: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">{t("profile.pincode")}</label>
              <input className="pf-input" value={editingAddr?.pincode || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, pincode: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">{t("addressBook.country")}</label>
              <input className="pf-input" value={editingAddr?.country ?? "India"}
                onChange={(e) => setEditingAddr({ ...editingAddr, country: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">{t("profile.label")}</label>
              <select className="pf-input" value={editingAddr?.label || "HOME"}
                onChange={(e) => setEditingAddr({ ...editingAddr, label: e.target.value })}>
                <option value="HOME">{t("nav.home")}</option>
                <option value="WORK">{t("profile.workOffice")}</option>
                <option value="OTHER">{t("profile.other")}</option>
              </select>
            </div>
            <div className="pf-group">
              <label className="pf-label">{t("profile.landmark")}</label>
              <input className="pf-input" value={editingAddr?.landmark || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, landmark: e.target.value })} placeholder={t("profile.near")} />
            </div>
            <div className="pf-group full-width">
              <label className="pf-label">{t("profile.deliveryInstructions")}</label>
              <textarea className="pf-input pf-textarea" value={editingAddr?.deliveryInstructions || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, deliveryInstructions: e.target.value })} placeholder={t("profile.gateCodePreferredDeliveryTime")} />
            </div>
            <div className="pf-group full-width">
              <div className="pf-checkbox-group">
                <label className="pf-checkbox">
                  <input type="checkbox" checked={editingAddr?.isDefault || false}
                    onChange={(e) => setEditingAddr({ ...editingAddr, isDefault: e.target.checked })} />{t("profile.setAsDefaultShippingAddress")}</label>
                <label className="pf-checkbox">
                  <input type="checkbox" checked={editingAddr?.isBilling || false}
                    onChange={(e) => setEditingAddr({ ...editingAddr, isBilling: e.target.checked })} />{t("profile.useAsBillingAddress")}</label>
              </div>
            </div>
          </div>
          <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.75rem" }}>
            <button className="pf-btn pf-btn-primary" onClick={handleAddAddress} disabled={saving}>
              {saving ? t("addressBook.saving") : (editingAddr?.id ? t("addressBook.update") : t("addressBook.addAddress"))}
            </button>
            <button className="pf-btn pf-btn-secondary" onClick={() => { setShowAddrForm(false); setEditingAddr(null); }}>{t("addressBook.cancel")}</button>
          </div>
        </div>
      )}

      {addresses.length === 0 && !showAddrForm && (
        <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", textAlign: "center" }}>{t("profile.noSavedAddressesClickAdd")}</div>
      )}

      <div className="profile-addr-list">
        {addresses.map((addr) => {
          const aid = addr.id || addr._id;
          return (
            <div key={aid} className={`profile-addr-card ${addr.isDefault ? "default" : ""}`}>
              <div className="profile-addr-header">
                <span className="profile-addr-label">{addr.label || "ADDRESS"}</span>
                {addr.isDefault && <span className="profile-badge-default">{t("addressBook.default")}</span>}
                {addr.isBilling && <span className="profile-badge-billing">{t("profile.billing")}</span>}
              </div>
              <div className="profile-addr-details">
                <div className="profile-addr-name">{addr.fullName}</div>
                {/* Same precedence as the server-side formatters: line1 then street, so an
                    address renders identically here, on the invoice and in the email. */}
                <div className="profile-addr-line">{addr.line1 || addr.street}</div>
                {addr.line2 && <div className="profile-addr-line">{addr.line2}</div>}
                <div className="profile-addr-line">{addr.city}, {addr.state} - {addr.pincode}</div>
                {addr.country && addr.country.trim().toLowerCase() !== "india" && (
                  <div className="profile-addr-line">{addr.country}</div>
                )}
                {addr.landmark && <div className="profile-addr-line">{t("profile.near3t6")}{" "}{addr.landmark}</div>}
                <div className="profile-addr-phone">{t("checkout.phone")}{" "}{addr.phone}</div>
                {addr.deliveryInstructions && <div className="profile-addr-instr">{t("profile.note")}{" "}{addr.deliveryInstructions}</div>}
              </div>
              <div className="profile-addr-actions">
                <button className="pf-btn-sm pf-btn-edit" onClick={() => {
                  // Seeded from street when line1 is empty, so opening an address saved before
                  // the split and pressing Update actually migrates it rather than saving the
                  // old shape back unchanged.
                  setEditingAddr({ ...addr, line1: addr.line1 || addr.street || "", country: addr.country || "India" });
                  setShowAddrForm(true);
                }}>{t("addressBook.edit")}</button>
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleDeleteAddress(aid)}>{t("addressBook.delete")}</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  const renderPayment = () => (
    <div className="profile-section-card">
      <div className="profile-card-header">
        <h3>{t("paymentMethods.paymentMethods")}</h3>
        <button className="pf-btn pf-btn-outline" onClick={() => setShowPaymentForm(!showPaymentForm)}>
          {showPaymentForm ? t("addressBook.cancel") : i18n.t("msgs.addPayment")}
        </button>
      </div>

      {showPaymentForm && (
        <div className="profile-addr-form">
          <h4 style={{ marginBottom: "0.75rem", fontWeight: 600 }}>{t("paymentMethods.newPaymentMethod")}</h4>
          <div className="profile-form-grid two-col">
            <div className="pf-group">
              <label className="pf-label">{t("paymentMethods.type")}</label>
              <select className="pf-input" value={paymentForm.type}
                onChange={(e) => setPaymentForm({ ...paymentForm, type: e.target.value })}>
                <option value="CARD">{t("checkout.creditDebitCard")}</option>
                <option value="UPI">UPI</option>
                <option value="NET_BANKING">{t("footer.netBanking")}</option>
              </select>
            </div>
            {paymentForm.type === "CARD" && (
              <>
                <div className="pf-group">
                  <label className="pf-label">{t("profile.cardNumber")}</label>
                  <input className="pf-input" value={paymentForm.maskedNumber}
                    onChange={(e) => setPaymentForm({ ...paymentForm, maskedNumber: e.target.value })}
                    placeholder={t("profile.xxxxXxxxXxxx1234")} maxLength="19" />
                </div>
                <div className="pf-group">
                  <label className="pf-label">{t("paymentMethods.cardholderName")}</label>
                  <input className="pf-input" value={paymentForm.cardholderName}
                    onChange={(e) => setPaymentForm({ ...paymentForm, cardholderName: e.target.value })} />
                </div>
                <div className="pf-group">
                  <label className="pf-label">{t("profile.expiryMmYy")}</label>
                  <input className="pf-input" value={paymentForm.expiry}
                    onChange={(e) => setPaymentForm({ ...paymentForm, expiry: e.target.value })}
                    placeholder={t("paymentMethods.mmYy")} maxLength="5" />
                </div>
              </>
            )}
            {paymentForm.type === "UPI" && (
              <div className="pf-group">
                <label className="pf-label">{t("paymentMethods.upiId")}</label>
                <input className="pf-input" value={paymentForm.upiId}
                  onChange={(e) => setPaymentForm({ ...paymentForm, upiId: e.target.value })}
                  placeholder={t("profile.usernameUpi")} />
              </div>
            )}
            {paymentForm.type === "NET_BANKING" && (
              <div className="pf-group">
                <label className="pf-label">{t("paymentMethods.bankName")}</label>
                <input className="pf-input" value={paymentForm.bankName}
                  onChange={(e) => setPaymentForm({ ...paymentForm, bankName: e.target.value })}
                  placeholder={t("profile.eGHdfcSbi")} />
              </div>
            )}
          </div>
          <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.75rem" }}>
            <button className="pf-btn pf-btn-primary" onClick={handleAddPayment} disabled={saving}>
              {saving ? t("addressBook.saving") : t("profile.addPaymentMethod")}
            </button>
            <button className="pf-btn pf-btn-secondary" onClick={() => setShowPaymentForm(false)}>{t("addressBook.cancel")}</button>
          </div>
        </div>
      )}

      {paymentMethods.length === 0 && !showPaymentForm && (
        <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", textAlign: "center" }}>{t("profile.noSavedPaymentMethods")}</div>
      )}

      <div className="profile-addr-list">
        {paymentMethods.map((pm) => {
          const pmId = pm.id || pm._id;
          const icon = pm.type === "UPI" ? "U" : pm.type === "NET_BANKING" ? "B" : "C";
          return (
            <div key={pmId} className="profile-addr-card">
              <div className="profile-addr-header">
                <span className="profile-pm-type">{icon === "C" ? "💳" : icon === "U" ? "📱" : "🏦"} {pm.type.replace("_", " ")}</span>
                {pm.isDefault && <span className="profile-badge-default">{t("addressBook.default")}</span>}
              </div>
              <div className="profile-addr-details">
                {pm.type === "CARD" && (
                  <>
                    <div className="profile-addr-name">{pm.cardholderName}</div>
                    <div className="profile-addr-line">{pm.maskedNumber}</div>
                    {pm.expiry && <div className="profile-addr-line">{t("profile.expires")}{" "}{pm.expiry}</div>}
                  </>
                )}
                {pm.type === "UPI" && <div className="profile-addr-name">{pm.upiId}</div>}
                {pm.type === "NET_BANKING" && <div className="profile-addr-name">{pm.bankName}</div>}
              </div>
              <div className="profile-addr-actions">
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleDeletePayment(pmId)}>{t("cart.remove")}</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  const renderSavedItems = () => (
    <div className="profile-section-card">
      <div className="profile-card-header"><h3>{t("nav.wishlist")}</h3></div>
      {wishlist.length === 0 ? (
        <div style={{ padding: "0.75rem 0", fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>{t("profile.noItemsInWishlist")}{" "}<Link to="/products">{t("cartDrawer.browseProducts")}</Link>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem", marginBottom: "1rem" }}>
          {wishlist.map((item) => {
            const p = item.product || {};
            const pid = p.id || p._id || item.productId;
            return (
              <div key={item.id || item._id} style={{
                display: "flex", alignItems: "center", justifyContent: "space-between",
                padding: "0.6rem 0.9rem", background: "var(--color-bg-secondary)", borderRadius: "var(--radius-sm)"
              }}>
                <Link to={`/product/${pid}`} style={{ fontSize: "0.9rem", color: "var(--color-text-primary)", textDecoration: "none" }}>
                  {p.name || item.productName || `Product #${pid}`}
                </Link>
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleRemoveWishlistItem(pid)}>{t("cart.remove")}</button>
              </div>
            );
          })}
        </div>
      )}

      <div className="profile-card-header"><h3>{t("cart.savedForLater")}</h3></div>
      {savedForLater.length === 0 ? (
        <div style={{ padding: "0.75rem 0", fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>{t("profile.noItemsSavedForLater")}{" "}<Link to="/products">{t("cartDrawer.browseProducts")}</Link>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
          {savedForLater.map((item) => {
            const p = item.product || {};
            const pid = p.id || p._id || item.productId;
            const itemId = item.id || item._id;
            return (
              <div key={itemId} style={{
                display: "flex", alignItems: "center", justifyContent: "space-between",
                padding: "0.6rem 0.9rem", background: "var(--color-bg-secondary)", borderRadius: "var(--radius-sm)"
              }}>
                <Link to={`/product/${pid}`} style={{ fontSize: "0.9rem", color: "var(--color-text-primary)", textDecoration: "none" }}>
                  {p.name || item.productName || `Product #${pid}`}
                </Link>
                <button className="pf-btn-sm pf-btn-primary" onClick={() => handleMoveSavedToCart(itemId)}>{t("cart.moveToCart")}</button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );

  const renderPreferences = () => (
    <div className="profile-section-card">
      <div className="profile-card-header"><h3>{t("profile.shoppingPreferences")}</h3></div>
      <div className="profile-form-grid two-col">
        <div className="pf-group">
          <label className="pf-label">{t("profile.preferredCategories")}</label>
          <input className="pf-input" value={preferences?.preferredCategories || ""}
            onChange={(e) => setPreferences({ ...preferences, preferredCategories: e.target.value })}
            placeholder={t("profile.electronicsFashionBooks")} />
        </div>
        <div className="pf-group">
          <label className="pf-label">{t("profile.preferredBrands")}</label>
          <input className="pf-input" value={preferences?.preferredBrands || ""}
            onChange={(e) => setPreferences({ ...preferences, preferredBrands: e.target.value })}
            placeholder={t("profile.appleSamsungNike")} />
        </div>
      </div>

      <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1rem 0 0.5rem" }}>{t("profile.subscriptions")}</h4>
      <div className="pf-group">
        <textarea className="pf-input pf-textarea" value={preferences?.subscriptions || ""}
          onChange={(e) => setPreferences({ ...preferences, subscriptions: e.target.value })}
          placeholder={t("profile.primeMembershipRecurringOrdersEtc")} rows={2} />
      </div>

      <div className="profile-form-actions">
        <button className="pf-btn pf-btn-primary" onClick={() => handlePrefsSave({})} disabled={saving}>
          {saving ? t("addressBook.saving") : t("profile.savePreferences")}
        </button>
      </div>
    </div>
  );

  const renderOrders = () => (
    <div className="profile-section-card">
      <div className="profile-card-header"><h3>{t("profile.orderHistory")}</h3></div>
      {orders.length === 0 ? (
        <div style={{ padding: "1.5rem 0", textAlign: "center", color: "var(--color-text-secondary)" }}>{t("orders.noOrdersYet")}</div>
      ) : (
        <div className="profile-orders-list">
          {orders.map((order) => {
            const oid = order.id || order._id;
            const orderNum = order.orderId || oid?.toString().slice(-8);
            const statusColor = order.status === "DELIVERED" ? "#0E5C5C" :
              order.status === "CANCELLED" ? "#dc2626" : "#f59e0b";
            return (
              <div key={oid} className="profile-order-card">
                <div className="profile-order-header">
                  <div>
                    <span className="profile-order-id">{t("orderDetail.order")}{orderNum}</span>
                    <span className="profile-order-status" style={{ color: statusColor }}>{order.status}</span>
                  </div>
                  <div className="profile-order-date">
                    {order.createdAt ? new Date(order.createdAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : ""}
                  </div>
                </div>
                <div className="profile-order-summary">
                  <span>&#8377;{(order.totalAmount || order.total || 0).toFixed(2)}</span>
                  <span style={{ color: "var(--color-text-secondary)", fontSize: "0.8rem" }}>{order.paymentMethod || t("orderDetail.online")}</span>
                  {(order.paymentStatus === "PAID" || order.paymentStatus === "COMPLETED") && (
                    <span style={{ color: "#0E5C5C", fontSize: "0.8rem" }}>{t("profile.paid")}</span>
                  )}
                </div>
                <div className="profile-order-actions">
                  <a href={`/orders/${oid}`} className="pf-btn-sm pf-btn-view">{t("orders.viewDetails")}</a>
                  {(order.status === "SHIPPED" || order.status === "DELIVERED") && order.trackingNumber && (
                    <span style={{ fontSize: "0.75rem", color: "var(--color-text-secondary)" }}>{t("profile.tracking")}{" "}{order.trackingNumber}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );

  const renderReviews = () => (
    <div className="profile-section-card">
      <div className="profile-card-header"><h3>{t("profile.myReviewsRatings")}</h3></div>
      {reviews.length === 0 ? (
        <div style={{ padding: "1.5rem 0", textAlign: "center", color: "var(--color-text-secondary)" }}>{t("profile.noReviewsYet")}</div>
      ) : (
        <div className="profile-orders-list">
          {reviews.map((review) => {
            const rId = review.id || review._id;
            return (
              <div key={rId} className="profile-order-card">
                <div className="profile-order-header">
                  <div style={{ fontWeight: 500, fontSize: "0.9rem" }}>
                    {review.product?.name || `Product #${review.product?.id || review.productId}`}
                  </div>
                  <div style={{ color: "#f59e0b", fontSize: "0.9rem" }}>
                    {"★".repeat(review.rating)}{"☆".repeat(5 - review.rating)}
                  </div>
                </div>
                <div className="profile-order-summary" style={{ flexDirection: "column", alignItems: "flex-start", gap: "0.25rem" }}>
                  <div style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>
                    {review.comment || review.reviewText || t("profile.noComment")}
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "var(--color-text-secondary)" }}>
                    {review.createdAt ? new Date(review.createdAt).toLocaleDateString("en-IN") : ""}
                    {review.approved ? <span style={{ color: "#0E5C5C", marginLeft: "0.5rem" }}>{t("seller.translation.approved")}</span> :
                      <span style={{ color: "#f59e0b", marginLeft: "0.5rem" }}>{t("profile.pending")}</span>}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );

  const renderSecurity = () => {
    const mfaEnabled = !!me?.mfaEnabled;
    const now = Date.now();
    const pwExpiry = me?.passwordExpiresAt ? new Date(me.passwordExpiresAt) : null;
    const pwDaysLeft = pwExpiry ? Math.ceil((pwExpiry.getTime() - now) / 86400000) : null;

    return (
      <div className="profile-section-card">
        <div className="profile-card-header"><h3>{t("profile.securitySettings")}</h3></div>

        {securityMsg && <div className="profile-notification success">{securityMsg}</div>}
        {securityErr && <div className="profile-notification error">{securityErr}</div>}

        {pwDaysLeft !== null && pwDaysLeft <= 7 && (
          <div style={{ padding: "0.75rem", marginBottom: "1rem", borderRadius: "var(--radius-sm)", background: pwDaysLeft <= 0 ? "#fef2f2" : "#fffbeb", border: `1px solid ${pwDaysLeft <= 0 ? "#fecaca" : "#fde68a"}`, fontSize: "0.85rem", color: pwDaysLeft <= 0 ? "#991b1b" : "#92400e" }}>
            {pwDaysLeft <= 0 ? t("profile.yourPasswordHasExpiredPlease") : i18n.t("msgs.pwExpiresUpdate", { count: pwDaysLeft })}
          </div>
        )}

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.75rem" }}>{t("profile.twoFactorAuthenticationTotp")}</h4>
        <p style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)", marginBottom: "0.75rem" }}>
          {mfaEnabled
            ? t("profile.twoFactorAuthenticationIsEnabled")
            : t("profile.addAnExtraLayerOf")}
        </p>

        {!mfaEnabled && !twofaSetup && (
          <button className="pf-btn pf-btn-primary" onClick={handleEnable2fa} disabled={mfaBusy}>
            {mfaBusy ? t("profile.settingUp") : t("profile.enable2fa")}
          </button>
        )}

        {!mfaEnabled && twofaSetup && (
          <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", padding: "1rem", marginBottom: "1rem", background: "var(--color-bg-secondary)" }}>
            <div style={{ fontWeight: 600, marginBottom: "0.5rem" }}>{t("profile.step1AddTheAccount")}</div>
            <p style={{ fontSize: "0.82rem", color: "var(--color-text-secondary)", marginBottom: "0.5rem" }}>{t("profile.openYourAuthenticatorAppAnd")}</p>
            <div style={{ fontSize: "0.82rem", wordBreak: "break-all", marginBottom: "0.5rem" }}>{t("profile.secret")}{" "}<strong>{twofaSetup.secret}</strong>
            </div>
            {twofaSetup.otpauthUrl && (
              <div style={{ fontSize: "0.8rem", wordBreak: "break-all", marginBottom: "0.75rem", color: "#2563eb" }}>
                <a href={twofaSetup.otpauthUrl} rel="noreferrer" target="_blank">{t("profile.openOtpauthLink")}</a>
              </div>
            )}
            <div style={{ fontWeight: 600, marginBottom: "0.5rem" }}>{t("profile.step2ConfirmWithA")}</div>
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              <input className="pf-input" value={mfaOtp} onChange={(e) => setMfaOtp(e.target.value.replace(/\D/g, "").slice(0, 6))} placeholder={t("profile.6DigitCode")} inputMode="numeric" style={{ width: "140px" }} />
              <button className="pf-btn pf-btn-primary" onClick={handleConfirm2fa} disabled={mfaBusy || mfaOtp.length < 6}>
                {mfaBusy ? t("profile.confirming") : t("profile.confirmEnable")}
              </button>
              <button className="pf-btn pf-btn-secondary" onClick={() => { setTwofaSetup(null); setMfaOtp(""); }} disabled={mfaBusy}>{t("addressBook.cancel")}</button>
            </div>
          </div>
        )}

        {mfaEnabled && (
          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "center" }}>
            <input className="pf-input" value={mfaOtp} onChange={(e) => setMfaOtp(e.target.value.replace(/\D/g, "").slice(0, 6))} placeholder={t("profile.current6DigitCode")} inputMode="numeric" style={{ width: "160px" }} />
            <button className="pf-btn pf-btn-secondary" onClick={handleDisable2fa} disabled={mfaBusy || mfaOtp.length < 6}>
              {mfaBusy ? t("profile.disabling") : t("profile.disable2fa")}
            </button>
          </div>
        )}

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>{t("profile.changePassword")}</h4>
        <div className="profile-form-grid two-col">
          <div className="pf-group">
            <label className="pf-label">{t("profile.currentPassword")}</label>
            <input className="pf-input" type="password" value={pwForm.oldPassword}
              onChange={(e) => setPwForm({ ...pwForm, oldPassword: e.target.value })} />
          </div>
          <div className="pf-group">
            <label className="pf-label">{t("profile.newPassword")}</label>
            <input className="pf-input" type="password" value={pwForm.newPassword}
              onChange={(e) => setPwForm({ ...pwForm, newPassword: e.target.value })} placeholder={t("profile.min8Characters")} />
          </div>
          <div className="pf-group">
            <label className="pf-label">{t("profile.confirmNewPassword")}</label>
            <input className="pf-input" type="password" value={pwForm.confirmPassword}
              onChange={(e) => setPwForm({ ...pwForm, confirmPassword: e.target.value })} />
          </div>
        </div>
        <div className="profile-form-actions">
          <button className="pf-btn pf-btn-primary" onClick={handleChangePassword} disabled={mfaBusy}>
            {mfaBusy ? t("addressBook.saving") : t("profile.updatePassword")}
          </button>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>{t("profile.activeSessions")}{sessions.length})</h4>
        {loadingSecurity ? (
          <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", fontSize: "0.85rem" }}>{t("profile.loadingSessions")}</div>
        ) : sessions.length === 0 ? (
          <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", fontSize: "0.85rem" }}>{t("profile.noActiveSessionsFound")}</div>
        ) : (
          <div className="profile-orders-list">
            {sessions.map((s) => (
              <div key={s.id} className="profile-order-card" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.5rem" }}>
                <div>
                  <div style={{ fontWeight: 500, fontSize: "0.9rem" }}>{s.deviceLabel || t("profile.device")}</div>
                  <div style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)" }}>
                    {s.ipAddress ? `${s.ipAddress} · ` : ""}{t("profile.signedIn")}{" "}{s.createdAt ? new Date(s.createdAt).toLocaleString("en-IN") : ""}
                    {s.expiresAt ? ` · Expires ${new Date(s.expiresAt).toLocaleString("en-IN")}` : ""}
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#0E5C5C" }}>{s.status}</div>
                </div>
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleRevokeSession(s.id)} disabled={sessionBusy === s.id}>
                  {sessionBusy === s.id ? "..." : t("profile.signOut")}
                </button>
              </div>
            ))}
          </div>
        )}
        <div className="profile-form-actions" style={{ marginTop: "0.75rem" }}>
          <button className="pf-btn pf-btn-secondary" onClick={handleLogoutAll}>{t("profile.signOutOfAllDevices")}</button>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>{t("profile.communicationPreferences")}</h4>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">{t("profile.emailNotifications")}</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.emailNotifications !== false}
              onChange={(e) => handlePrefsSave({ emailNotifications: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">{t("profile.smsNotifications")}</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.smsNotifications !== false}
              onChange={(e) => handlePrefsSave({ smsNotifications: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">{t("profile.pushNotifications")}</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.pushNotifications !== false}
              onChange={(e) => handlePrefsSave({ pushNotifications: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>{t("profile.privacyGdpr")}</h4>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">{t("profile.iConsentToDataProcessing")}</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.gdprConsent || false}
              onChange={(e) => handlePrefsSave({ gdprConsent: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>
        <div style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)", marginTop: "0.75rem", padding: "0.75rem", background: "var(--color-bg-secondary)", borderRadius: "var(--radius-sm)" }}>{t("profile.yourDataIsHandledIn")}</div>
      </div>
    );
  };

  return (
    <div className="profile-page">
      {renderNotification()}
      <div className="profile-header">
        <h1 className="profile-title">{t("nav.account")}</h1>
      </div>
      <div className="profile-layout">
        <div className="profile-tabs">
          {TABS.map((tab) => (
            <button key={tab.key}
              className={`profile-tab ${activeTab === tab.key ? "active" : ""}`}
              onClick={() => setActiveTab(tab.key)}>
              {tab.label}
            </button>
          ))}
        </div>
        <div className="profile-content">
          {renderTab(activeTab)}
        </div>
      </div>
    </div>
  );
};

export default Profile;
