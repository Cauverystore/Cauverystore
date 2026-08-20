import React, { useState, useEffect, useCallback } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import userService from "../services/userService";
import securityService from "../services/securityService";
import { useAuth } from "../context/AuthContext";
import { getCart, moveToCart } from "../services/cartService";
import { removeFromWishlist } from "../services/wishlistService";
import { useWishlist } from "../context/WishlistContext";
import "../styles/profile.css";

const PLACEHOLDER = "/images/placeholder.svg";

const TABS = [
  { key: "personal", label: "Personal Info" },
  { key: "addresses", label: "Addresses" },
  { key: "payment", label: "Payment Methods" },
  { key: "saved", label: "Saved Items" },
  { key: "preferences", label: "Preferences" },
  { key: "orders", label: "Orders" },
  { key: "reviews", label: "Reviews" },
  { key: "security", label: "Security" },
];

// line1/line2 are the current shape; street is kept because addresses saved before the split
// still carry it, and an edit form that ignored it would blank a line the customer never touched.
const initialAddr = () => ({
  fullName: "", phone: "", line1: "", line2: "", street: "", city: "", state: "", pincode: "",
  country: "India",
  label: "HOME", landmark: "", deliveryInstructions: "", isDefault: false, isBilling: false
});

const Profile = () => {
  const navigate = useNavigate();
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
      setError("Failed to load profile data");
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
      notify("Removed from wishlist");
    } catch (err) { notify("Failed to remove item", "error"); }
  };

  const handleMoveSavedToCart = async (itemId) => {
    try {
      await moveToCart(itemId);
      setSavedForLater((prev) => prev.filter((i) => (i.id || i._id) !== itemId));
      notify("Moved to cart");
    } catch (err) { notify("Failed to move item to cart", "error"); }
  };

  const handleProfileSave = async () => {
    setSaving(true); setError(""); setSuccess("");
    try {
      const res = await userService.updateProfile(profileForm);
      setProfile(res.data);
      notify("Profile updated successfully");
    } catch (err) { notify(err.response?.data?.message || "Failed to update profile", "error"); }
    setSaving(false);
  };

  const handleAddAddress = async () => {
    // A legacy address supplies its first line as street, a new one as line1. Requiring line1
    // outright would make an old address unsaveable until it was retyped.
    const firstLine = editingAddr.line1?.trim() || editingAddr.street?.trim();
    if (!editingAddr.fullName?.trim() || !firstLine || !editingAddr.city?.trim() || !editingAddr.state?.trim() || !editingAddr.pincode?.trim()) {
      notify("Please fill all required fields", "error"); return;
    }
    setSaving(true);
    try {
      const res = editingAddr.id
        ? await userService.updateAddress(editingAddr.id, editingAddr)
        : await userService.addAddress(editingAddr);
      await loadAll();
      setShowAddrForm(false); setEditingAddr(null);
      notify(`Address ${editingAddr.id ? "updated" : "added"} successfully`);
    } catch (err) { notify(err.response?.data?.message || "Failed to save address", "error"); }
    setSaving(false);
  };

  const handleDeleteAddress = async (id) => {
    if (!window.confirm("Delete this address?")) return;
    try {
      await userService.deleteAddress(id);
      setAddresses(addresses.filter(a => (a.id || a._id) !== id));
      notify("Address deleted");
    } catch (err) {
      // The server refuses to delete an address an order still points at, and says why - it is
      // needed for the invoice and for the delivery. Replacing that with "Failed to delete"
      // leaves somebody clicking a button that will never work and no idea what to do instead.
      notify(err.response?.data?.error || err.response?.data?.message || "Failed to delete address", "error");
    }
  };

  const handleAddPayment = async () => {
    if (paymentForm.type === "CARD" && !paymentForm.maskedNumber?.trim()) {
      notify("Card number is required", "error"); return;
    }
    if (paymentForm.type === "UPI" && !paymentForm.upiId?.trim()) {
      notify("UPI ID is required", "error"); return;
    }
    setSaving(true);
    try {
      await userService.addPaymentMethod(paymentForm);
      const res = await userService.getPaymentMethods();
      setPaymentMethods(Array.isArray(res.data) ? res.data : []);
      setShowPaymentForm(false);
      setPaymentForm({ type: "CARD", maskedNumber: "", cardholderName: "", expiry: "", upiId: "", bankName: "" });
      notify("Payment method added");
    } catch (err) { notify("Failed to add payment method", "error"); }
    setSaving(false);
  };

  const handleDeletePayment = async (id) => {
    if (!window.confirm("Remove this payment method?")) return;
    try {
      await userService.deletePaymentMethod(id);
      setPaymentMethods(paymentMethods.filter(p => (p.id || p._id) !== id));
      notify("Payment method removed");
    } catch (err) { notify("Failed to remove payment method", "error"); }
  };

  const handlePrefsSave = async (updates) => {
    setSaving(true);
    try {
      const res = await userService.updatePreferences({ ...preferences, ...updates });
      setPreferences(res.data);
      notify("Preferences saved");
    } catch (err) { notify("Failed to save preferences", "error"); }
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
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || "Failed to start 2FA setup", "error"); }
    setMfaBusy(false);
  };

  const handleConfirm2fa = async () => {
    if (!mfaOtp.trim()) { secNotify("Enter the code from your authenticator app", "error"); return; }
    setMfaBusy(true); setSecurityErr("");
    try {
      await securityService.confirm2fa(mfaOtp.trim());
      setTwofaSetup(null); setMfaOtp("");
      secNotify("Two-factor authentication enabled");
      await loadSecurity();
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || "Invalid code. Try again.", "error"); }
    setMfaBusy(false);
  };

  const handleDisable2fa = async () => {
    if (!mfaOtp.trim()) { secNotify("Enter a current code to disable 2FA", "error"); return; }
    setMfaBusy(true); setSecurityErr("");
    try {
      await securityService.disable2fa(mfaOtp.trim());
      setMfaOtp("");
      secNotify("Two-factor authentication disabled");
      await loadSecurity();
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || "Invalid code. Try again.", "error"); }
    setMfaBusy(false);
  };

  const handleChangePassword = async () => {
    if (!pwForm.oldPassword || !pwForm.newPassword) { secNotify("Enter your current and new password", "error"); return; }
    if (pwForm.newPassword.length < 8) { secNotify("New password must be at least 8 characters", "error"); return; }
    if (pwForm.newPassword !== pwForm.confirmPassword) { secNotify("New passwords do not match", "error"); return; }
    setMfaBusy(true); setSecurityErr("");
    try {
      await securityService.changePassword(pwForm.oldPassword, pwForm.newPassword);
      setPwForm({ oldPassword: "", newPassword: "", confirmPassword: "" });
      secNotify("Password changed successfully");
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || "Failed to change password", "error"); }
    setMfaBusy(false);
  };

  const handleRevokeSession = async (sessionId) => {
    if (!window.confirm("Sign out this device?")) return;
    setSessionBusy(sessionId);
    try {
      await securityService.revokeSession(sessionId);
      await loadSecurity();
      secNotify("Session revoked");
    } catch (err) { secNotify(err.response?.data?.error || err.response?.data?.message || "Failed to revoke session", "error"); }
    setSessionBusy(null);
  };

  const handleLogoutAll = async () => {
    if (!window.confirm("Sign out from all devices? You will be logged out of this device too.")) return;
    try {
      await securityService.logoutAll();
    } catch { /* ignore */ }
    await logout();
    navigate("/login");
  };

  if (loading) {
    return <div style={{ textAlign: "center", padding: "3rem", color: "var(--color-text-secondary)" }}>Loading profile...</div>;
  }

  const userId = profile?.id || "";
  const userRole = profile?.role || "";
  const userLabel = userRole === "SELLER" ? "Seller ID" : userRole === "CUSTOMER" ? "Customer ID" : "User ID";
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
        <h3>Personal Information</h3>
        {userId && <span className="profile-customer-id">{userLabel}: {userId.toString().slice(-8).toUpperCase()}</span>}
      </div>
      <div className="profile-avatar-section">
        <img src={profilePic} alt="" width="80" height="80" className="profile-avatar"
          onError={(e) => { e.target.src = PLACEHOLDER; }} />
        <div>
          <div className="profile-name-display">{profile?.fullName || profile?.name}</div>
          <div className="profile-email-display">{profile?.email}</div>
        </div>
      </div>
      <div className="profile-form-grid">
        <div className="pf-group">
          <label className="pf-label">Full Name</label>
          <input className="pf-input" value={profileForm.fullName || ""}
            onChange={(e) => setProfileForm({ ...profileForm, fullName: e.target.value })} />
        </div>
        <div className="pf-group">
          <label className="pf-label">Email Address</label>
          <input className="pf-input" value={profileForm.email || ""}
            onChange={(e) => setProfileForm({ ...profileForm, email: e.target.value })} />
        </div>
        <div className="pf-group">
          <label className="pf-label">Phone Number</label>
          <input className="pf-input" value={profileForm.phone || ""}
            onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })} />
        </div>
      </div>
      <div className="profile-form-actions">
        <button className="pf-btn pf-btn-primary" onClick={handleProfileSave} disabled={saving}>
          {saving ? "Saving..." : "Save Changes"}
        </button>
      </div>
    </div>
  );

  const renderAddresses = () => (
    <div className="profile-section-card">
      <div className="profile-card-header">
        <h3>Saved Addresses</h3>
        <button className="pf-btn pf-btn-outline" onClick={() => { setEditingAddr(initialAddr()); setShowAddrForm(true); }}>+ Add New</button>
      </div>

      {showAddrForm && (
        <div className="profile-addr-form">
          <h4 style={{ marginBottom: "0.75rem", fontWeight: 600 }}>{editingAddr?.id ? "Edit Address" : "New Address"}</h4>
          <div className="profile-form-grid two-col">
            <div className="pf-group">
              <label className="pf-label">Full Name *</label>
              <input className="pf-input" value={editingAddr?.fullName || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, fullName: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">Phone *</label>
              <input className="pf-input" value={editingAddr?.phone || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, phone: e.target.value })} />
            </div>
            <div className="pf-group full-width">
              <label className="pf-label">Address line 1 *</label>
              {/* Falls back to street so an address saved before the split shows what it holds
                  rather than an empty box the customer would have to fill in again. */}
              <input className="pf-input" value={editingAddr?.line1 ?? editingAddr?.street ?? ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, line1: e.target.value })}
                placeholder="House / flat number and street" />
            </div>
            <div className="pf-group full-width">
              <label className="pf-label">Address line 2</label>
              <input className="pf-input" value={editingAddr?.line2 || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, line2: e.target.value })}
                placeholder="Area, locality (optional)" />
            </div>
            <div className="pf-group">
              <label className="pf-label">City *</label>
              <input className="pf-input" value={editingAddr?.city || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, city: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">State *</label>
              <input className="pf-input" value={editingAddr?.state || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, state: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">Pincode *</label>
              <input className="pf-input" value={editingAddr?.pincode || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, pincode: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">Country</label>
              <input className="pf-input" value={editingAddr?.country ?? "India"}
                onChange={(e) => setEditingAddr({ ...editingAddr, country: e.target.value })} />
            </div>
            <div className="pf-group">
              <label className="pf-label">Label</label>
              <select className="pf-input" value={editingAddr?.label || "HOME"}
                onChange={(e) => setEditingAddr({ ...editingAddr, label: e.target.value })}>
                <option value="HOME">Home</option>
                <option value="WORK">Work / Office</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <div className="pf-group">
              <label className="pf-label">Landmark</label>
              <input className="pf-input" value={editingAddr?.landmark || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, landmark: e.target.value })} placeholder="Near..." />
            </div>
            <div className="pf-group full-width">
              <label className="pf-label">Delivery Instructions</label>
              <textarea className="pf-input pf-textarea" value={editingAddr?.deliveryInstructions || ""}
                onChange={(e) => setEditingAddr({ ...editingAddr, deliveryInstructions: e.target.value })} placeholder="Gate code, preferred delivery time, etc." />
            </div>
            <div className="pf-group full-width">
              <div className="pf-checkbox-group">
                <label className="pf-checkbox">
                  <input type="checkbox" checked={editingAddr?.isDefault || false}
                    onChange={(e) => setEditingAddr({ ...editingAddr, isDefault: e.target.checked })} />
                  Set as default shipping address
                </label>
                <label className="pf-checkbox">
                  <input type="checkbox" checked={editingAddr?.isBilling || false}
                    onChange={(e) => setEditingAddr({ ...editingAddr, isBilling: e.target.checked })} />
                  Use as billing address
                </label>
              </div>
            </div>
          </div>
          <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.75rem" }}>
            <button className="pf-btn pf-btn-primary" onClick={handleAddAddress} disabled={saving}>
              {saving ? "Saving..." : (editingAddr?.id ? "Update" : "Add Address")}
            </button>
            <button className="pf-btn pf-btn-secondary" onClick={() => { setShowAddrForm(false); setEditingAddr(null); }}>Cancel</button>
          </div>
        </div>
      )}

      {addresses.length === 0 && !showAddrForm && (
        <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", textAlign: "center" }}>
          No saved addresses. Click "Add New" to add one.
        </div>
      )}

      <div className="profile-addr-list">
        {addresses.map((addr) => {
          const aid = addr.id || addr._id;
          return (
            <div key={aid} className={`profile-addr-card ${addr.isDefault ? "default" : ""}`}>
              <div className="profile-addr-header">
                <span className="profile-addr-label">{addr.label || "ADDRESS"}</span>
                {addr.isDefault && <span className="profile-badge-default">Default</span>}
                {addr.isBilling && <span className="profile-badge-billing">Billing</span>}
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
                {addr.landmark && <div className="profile-addr-line">Near: {addr.landmark}</div>}
                <div className="profile-addr-phone">Phone: {addr.phone}</div>
                {addr.deliveryInstructions && <div className="profile-addr-instr">Note: {addr.deliveryInstructions}</div>}
              </div>
              <div className="profile-addr-actions">
                <button className="pf-btn-sm pf-btn-edit" onClick={() => {
                  // Seeded from street when line1 is empty, so opening an address saved before
                  // the split and pressing Update actually migrates it rather than saving the
                  // old shape back unchanged.
                  setEditingAddr({ ...addr, line1: addr.line1 || addr.street || "", country: addr.country || "India" });
                  setShowAddrForm(true);
                }}>Edit</button>
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleDeleteAddress(aid)}>Delete</button>
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
        <h3>Payment Methods</h3>
        <button className="pf-btn pf-btn-outline" onClick={() => setShowPaymentForm(!showPaymentForm)}>
          {showPaymentForm ? "Cancel" : "+ Add Payment"}
        </button>
      </div>

      {showPaymentForm && (
        <div className="profile-addr-form">
          <h4 style={{ marginBottom: "0.75rem", fontWeight: 600 }}>New Payment Method</h4>
          <div className="profile-form-grid two-col">
            <div className="pf-group">
              <label className="pf-label">Type</label>
              <select className="pf-input" value={paymentForm.type}
                onChange={(e) => setPaymentForm({ ...paymentForm, type: e.target.value })}>
                <option value="CARD">Credit / Debit Card</option>
                <option value="UPI">UPI</option>
                <option value="NET_BANKING">Net Banking</option>
              </select>
            </div>
            {paymentForm.type === "CARD" && (
              <>
                <div className="pf-group">
                  <label className="pf-label">Card Number</label>
                  <input className="pf-input" value={paymentForm.maskedNumber}
                    onChange={(e) => setPaymentForm({ ...paymentForm, maskedNumber: e.target.value })}
                    placeholder="XXXX XXXX XXXX 1234" maxLength="19" />
                </div>
                <div className="pf-group">
                  <label className="pf-label">Cardholder Name</label>
                  <input className="pf-input" value={paymentForm.cardholderName}
                    onChange={(e) => setPaymentForm({ ...paymentForm, cardholderName: e.target.value })} />
                </div>
                <div className="pf-group">
                  <label className="pf-label">Expiry (MM/YY)</label>
                  <input className="pf-input" value={paymentForm.expiry}
                    onChange={(e) => setPaymentForm({ ...paymentForm, expiry: e.target.value })}
                    placeholder="MM/YY" maxLength="5" />
                </div>
              </>
            )}
            {paymentForm.type === "UPI" && (
              <div className="pf-group">
                <label className="pf-label">UPI ID</label>
                <input className="pf-input" value={paymentForm.upiId}
                  onChange={(e) => setPaymentForm({ ...paymentForm, upiId: e.target.value })}
                  placeholder="username@upi" />
              </div>
            )}
            {paymentForm.type === "NET_BANKING" && (
              <div className="pf-group">
                <label className="pf-label">Bank Name</label>
                <input className="pf-input" value={paymentForm.bankName}
                  onChange={(e) => setPaymentForm({ ...paymentForm, bankName: e.target.value })}
                  placeholder="e.g. HDFC, SBI" />
              </div>
            )}
          </div>
          <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.75rem" }}>
            <button className="pf-btn pf-btn-primary" onClick={handleAddPayment} disabled={saving}>
              {saving ? "Saving..." : "Add Payment Method"}
            </button>
            <button className="pf-btn pf-btn-secondary" onClick={() => setShowPaymentForm(false)}>Cancel</button>
          </div>
        </div>
      )}

      {paymentMethods.length === 0 && !showPaymentForm && (
        <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", textAlign: "center" }}>
          No saved payment methods.
        </div>
      )}

      <div className="profile-addr-list">
        {paymentMethods.map((pm) => {
          const pmId = pm.id || pm._id;
          const icon = pm.type === "UPI" ? "U" : pm.type === "NET_BANKING" ? "B" : "C";
          return (
            <div key={pmId} className="profile-addr-card">
              <div className="profile-addr-header">
                <span className="profile-pm-type">{icon === "C" ? "💳" : icon === "U" ? "📱" : "🏦"} {pm.type.replace("_", " ")}</span>
                {pm.isDefault && <span className="profile-badge-default">Default</span>}
              </div>
              <div className="profile-addr-details">
                {pm.type === "CARD" && (
                  <>
                    <div className="profile-addr-name">{pm.cardholderName}</div>
                    <div className="profile-addr-line">{pm.maskedNumber}</div>
                    {pm.expiry && <div className="profile-addr-line">Expires {pm.expiry}</div>}
                  </>
                )}
                {pm.type === "UPI" && <div className="profile-addr-name">{pm.upiId}</div>}
                {pm.type === "NET_BANKING" && <div className="profile-addr-name">{pm.bankName}</div>}
              </div>
              <div className="profile-addr-actions">
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleDeletePayment(pmId)}>Remove</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  const renderSavedItems = () => (
    <div className="profile-section-card">
      <div className="profile-card-header"><h3>Wishlist</h3></div>
      {wishlist.length === 0 ? (
        <div style={{ padding: "0.75rem 0", fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>
          No items in wishlist. <Link to="/products">Browse products</Link>
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
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleRemoveWishlistItem(pid)}>Remove</button>
              </div>
            );
          })}
        </div>
      )}

      <div className="profile-card-header"><h3>Saved for Later</h3></div>
      {savedForLater.length === 0 ? (
        <div style={{ padding: "0.75rem 0", fontSize: "0.85rem", color: "var(--color-text-secondary)" }}>
          No items saved for later. <Link to="/products">Browse products</Link>
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
                <button className="pf-btn-sm pf-btn-primary" onClick={() => handleMoveSavedToCart(itemId)}>Move to Cart</button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );

  const renderPreferences = () => (
    <div className="profile-section-card">
      <div className="profile-card-header"><h3>Shopping Preferences</h3></div>
      <div className="profile-form-grid two-col">
        <div className="pf-group">
          <label className="pf-label">Preferred Categories</label>
          <input className="pf-input" value={preferences?.preferredCategories || ""}
            onChange={(e) => setPreferences({ ...preferences, preferredCategories: e.target.value })}
            placeholder="Electronics, Fashion, Books..." />
        </div>
        <div className="pf-group">
          <label className="pf-label">Preferred Brands</label>
          <input className="pf-input" value={preferences?.preferredBrands || ""}
            onChange={(e) => setPreferences({ ...preferences, preferredBrands: e.target.value })}
            placeholder="Apple, Samsung, Nike..." />
        </div>
      </div>

      <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1rem 0 0.5rem" }}>Subscriptions</h4>
      <div className="pf-group">
        <textarea className="pf-input pf-textarea" value={preferences?.subscriptions || ""}
          onChange={(e) => setPreferences({ ...preferences, subscriptions: e.target.value })}
          placeholder="Prime membership, recurring orders, etc." rows={2} />
      </div>

      <div className="profile-form-actions">
        <button className="pf-btn pf-btn-primary" onClick={() => handlePrefsSave({})} disabled={saving}>
          {saving ? "Saving..." : "Save Preferences"}
        </button>
      </div>
    </div>
  );

  const renderOrders = () => (
    <div className="profile-section-card">
      <div className="profile-card-header"><h3>Order History</h3></div>
      {orders.length === 0 ? (
        <div style={{ padding: "1.5rem 0", textAlign: "center", color: "var(--color-text-secondary)" }}>
          No orders yet
        </div>
      ) : (
        <div className="profile-orders-list">
          {orders.map((order) => {
            const oid = order.id || order._id;
            const orderNum = order.orderId || oid?.toString().slice(-8);
            const statusColor = order.status === "DELIVERED" ? "#16a34a" :
              order.status === "CANCELLED" ? "#dc2626" : "#f59e0b";
            return (
              <div key={oid} className="profile-order-card">
                <div className="profile-order-header">
                  <div>
                    <span className="profile-order-id">Order #{orderNum}</span>
                    <span className="profile-order-status" style={{ color: statusColor }}>{order.status}</span>
                  </div>
                  <div className="profile-order-date">
                    {order.createdAt ? new Date(order.createdAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : ""}
                  </div>
                </div>
                <div className="profile-order-summary">
                  <span>&#8377;{(order.totalAmount || order.total || 0).toFixed(2)}</span>
                  <span style={{ color: "var(--color-text-secondary)", fontSize: "0.8rem" }}>{order.paymentMethod || "COD"}</span>
                  {(order.paymentStatus === "PAID" || order.paymentStatus === "COMPLETED") && (
                    <span style={{ color: "#16a34a", fontSize: "0.8rem" }}>Paid</span>
                  )}
                </div>
                <div className="profile-order-actions">
                  <a href={`/orders/${oid}`} className="pf-btn-sm pf-btn-view">View Details</a>
                  {(order.status === "SHIPPED" || order.status === "DELIVERED") && order.trackingNumber && (
                    <span style={{ fontSize: "0.75rem", color: "var(--color-text-secondary)" }}>
                      Tracking: {order.trackingNumber}
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
      <div className="profile-card-header"><h3>My Reviews & Ratings</h3></div>
      {reviews.length === 0 ? (
        <div style={{ padding: "1.5rem 0", textAlign: "center", color: "var(--color-text-secondary)" }}>
          No reviews yet
        </div>
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
                    {review.comment || review.reviewText || "No comment"}
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "var(--color-text-secondary)" }}>
                    {review.createdAt ? new Date(review.createdAt).toLocaleDateString("en-IN") : ""}
                    {review.approved ? <span style={{ color: "#16a34a", marginLeft: "0.5rem" }}>Approved</span> :
                      <span style={{ color: "#f59e0b", marginLeft: "0.5rem" }}>Pending</span>}
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
        <div className="profile-card-header"><h3>Security & Settings</h3></div>

        {securityMsg && <div className="profile-notification success">{securityMsg}</div>}
        {securityErr && <div className="profile-notification error">{securityErr}</div>}

        {pwDaysLeft !== null && pwDaysLeft <= 7 && (
          <div style={{ padding: "0.75rem", marginBottom: "1rem", borderRadius: "var(--radius-sm)", background: pwDaysLeft <= 0 ? "#fef2f2" : "#fffbeb", border: `1px solid ${pwDaysLeft <= 0 ? "#fecaca" : "#fde68a"}`, fontSize: "0.85rem", color: pwDaysLeft <= 0 ? "#991b1b" : "#92400e" }}>
            {pwDaysLeft <= 0 ? "Your password has expired. Please update it to continue using your account securely." : `Your password expires in ${pwDaysLeft} day${pwDaysLeft === 1 ? "" : "s"}. Update it below.`}
          </div>
        )}

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, marginBottom: "0.75rem" }}>Two-Factor Authentication (TOTP)</h4>
        <p style={{ fontSize: "0.85rem", color: "var(--color-text-secondary)", marginBottom: "0.75rem" }}>
          {mfaEnabled
            ? "Two-factor authentication is enabled. Every sign-in will ask for a code from your authenticator app."
            : "Add an extra layer of security. Once enabled, sign-in will ask for a 6-digit code from your authenticator app (Google Authenticator, Microsoft Authenticator, Authy, etc.)."}
        </p>

        {!mfaEnabled && !twofaSetup && (
          <button className="pf-btn pf-btn-primary" onClick={handleEnable2fa} disabled={mfaBusy}>
            {mfaBusy ? "Setting up..." : "Enable 2FA"}
          </button>
        )}

        {!mfaEnabled && twofaSetup && (
          <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-sm)", padding: "1rem", marginBottom: "1rem", background: "var(--color-bg-secondary)" }}>
            <div style={{ fontWeight: 600, marginBottom: "0.5rem" }}>Step 1: Add the account to your authenticator app</div>
            <p style={{ fontSize: "0.82rem", color: "var(--color-text-secondary)", marginBottom: "0.5rem" }}>
              Open your authenticator app and add manually using this secret (or use this otpauth link on a device with a TOTP-capable app):
            </p>
            <div style={{ fontSize: "0.82rem", wordBreak: "break-all", marginBottom: "0.5rem" }}>
              Secret: <strong>{twofaSetup.secret}</strong>
            </div>
            {twofaSetup.otpauthUrl && (
              <div style={{ fontSize: "0.8rem", wordBreak: "break-all", marginBottom: "0.75rem", color: "#2563eb" }}>
                <a href={twofaSetup.otpauthUrl} rel="noreferrer" target="_blank">Open otpauth link</a>
              </div>
            )}
            <div style={{ fontWeight: 600, marginBottom: "0.5rem" }}>Step 2: Confirm with a code</div>
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              <input className="pf-input" value={mfaOtp} onChange={(e) => setMfaOtp(e.target.value.replace(/\D/g, "").slice(0, 6))} placeholder="6-digit code" inputMode="numeric" style={{ width: "140px" }} />
              <button className="pf-btn pf-btn-primary" onClick={handleConfirm2fa} disabled={mfaBusy || mfaOtp.length < 6}>
                {mfaBusy ? "Confirming..." : "Confirm & Enable"}
              </button>
              <button className="pf-btn pf-btn-secondary" onClick={() => { setTwofaSetup(null); setMfaOtp(""); }} disabled={mfaBusy}>Cancel</button>
            </div>
          </div>
        )}

        {mfaEnabled && (
          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "center" }}>
            <input className="pf-input" value={mfaOtp} onChange={(e) => setMfaOtp(e.target.value.replace(/\D/g, "").slice(0, 6))} placeholder="Current 6-digit code" inputMode="numeric" style={{ width: "160px" }} />
            <button className="pf-btn pf-btn-secondary" onClick={handleDisable2fa} disabled={mfaBusy || mfaOtp.length < 6}>
              {mfaBusy ? "Disabling..." : "Disable 2FA"}
            </button>
          </div>
        )}

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>Change Password</h4>
        <div className="profile-form-grid two-col">
          <div className="pf-group">
            <label className="pf-label">Current Password</label>
            <input className="pf-input" type="password" value={pwForm.oldPassword}
              onChange={(e) => setPwForm({ ...pwForm, oldPassword: e.target.value })} />
          </div>
          <div className="pf-group">
            <label className="pf-label">New Password</label>
            <input className="pf-input" type="password" value={pwForm.newPassword}
              onChange={(e) => setPwForm({ ...pwForm, newPassword: e.target.value })} placeholder="Min 8 characters" />
          </div>
          <div className="pf-group">
            <label className="pf-label">Confirm New Password</label>
            <input className="pf-input" type="password" value={pwForm.confirmPassword}
              onChange={(e) => setPwForm({ ...pwForm, confirmPassword: e.target.value })} />
          </div>
        </div>
        <div className="profile-form-actions">
          <button className="pf-btn pf-btn-primary" onClick={handleChangePassword} disabled={mfaBusy}>
            {mfaBusy ? "Saving..." : "Update Password"}
          </button>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>Active Sessions ({sessions.length})</h4>
        {loadingSecurity ? (
          <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", fontSize: "0.85rem" }}>Loading sessions...</div>
        ) : sessions.length === 0 ? (
          <div style={{ padding: "1rem 0", color: "var(--color-text-secondary)", fontSize: "0.85rem" }}>No active sessions found.</div>
        ) : (
          <div className="profile-orders-list">
            {sessions.map((s) => (
              <div key={s.id} className="profile-order-card" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.5rem" }}>
                <div>
                  <div style={{ fontWeight: 500, fontSize: "0.9rem" }}>{s.deviceLabel || "Device"}</div>
                  <div style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)" }}>
                    {s.ipAddress ? `${s.ipAddress} · ` : ""}Signed in {s.createdAt ? new Date(s.createdAt).toLocaleString("en-IN") : ""}
                    {s.expiresAt ? ` · Expires ${new Date(s.expiresAt).toLocaleString("en-IN")}` : ""}
                  </div>
                  <div style={{ fontSize: "0.75rem", color: "#16a34a" }}>{s.status}</div>
                </div>
                <button className="pf-btn-sm pf-btn-danger" onClick={() => handleRevokeSession(s.id)} disabled={sessionBusy === s.id}>
                  {sessionBusy === s.id ? "..." : "Sign Out"}
                </button>
              </div>
            ))}
          </div>
        )}
        <div className="profile-form-actions" style={{ marginTop: "0.75rem" }}>
          <button className="pf-btn pf-btn-secondary" onClick={handleLogoutAll}>Sign Out of All Devices</button>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>Communication Preferences</h4>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">Email Notifications</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.emailNotifications !== false}
              onChange={(e) => handlePrefsSave({ emailNotifications: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">SMS Notifications</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.smsNotifications !== false}
              onChange={(e) => handlePrefsSave({ smsNotifications: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">Push Notifications</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.pushNotifications !== false}
              onChange={(e) => handlePrefsSave({ pushNotifications: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>COD Preference</h4>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">Prefer Cash on Delivery</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.codPreference !== false}
              onChange={(e) => handlePrefsSave({ codPreference: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>

        <h4 style={{ fontSize: "0.95rem", fontWeight: 600, margin: "1.25rem 0 0.75rem" }}>Privacy & GDPR</h4>
        <div className="pf-toggle-row">
          <span className="pf-toggle-label">I consent to data processing as per GDPR</span>
          <label className="pf-switch">
            <input type="checkbox" checked={preferences?.gdprConsent || false}
              onChange={(e) => handlePrefsSave({ gdprConsent: e.target.checked })} />
            <span className="pf-slider"></span>
          </label>
        </div>
        <div style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)", marginTop: "0.75rem", padding: "0.75rem", background: "var(--color-bg-secondary)", borderRadius: "var(--radius-sm)" }}>
          Your data is handled in accordance with applicable privacy laws. You may request data deletion by contacting support.
        </div>
      </div>
    );
  };

  return (
    <div className="profile-page">
      {renderNotification()}
      <div className="profile-header">
        <h1 className="profile-title">My Account</h1>
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
