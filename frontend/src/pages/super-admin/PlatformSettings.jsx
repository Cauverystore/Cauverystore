import React, { useState, useEffect } from 'react';
import { Store, Lock, CreditCard, Mail, Receipt, Banknote } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../admin/context/ToastContext';

const TABS = [
  { id: 'store', label: 'Store Settings', icon: <Store size={18} /> },
  { id: 'security', label: 'Security', icon: <Lock size={18} /> },
  { id: 'payment', label: 'Payment', icon: <CreditCard size={18} /> },
  { id: 'cod', label: 'Cash on Delivery', icon: <Banknote size={18} /> },
  { id: 'email', label: 'Email', icon: <Mail size={18} /> },
  { id: 'tax', label: 'Tax / GST', icon: <Receipt size={18} /> },
];

const SECTION_NAMES = { store: 'Store', security: 'Security', payment: 'Payment', cod: 'Cash on delivery', email: 'Email', tax: 'Tax / GST' };

const initialSettings = {
  store: { name: 'Cauvery Store', description: '', currency: 'INR', timezone: 'Asia/Kolkata', contactEmail: '', phone: '', address: '' },
  security: { minPasswordLength: 8, maxLoginAttempts: 5, sessionTimeout: 60, requireOtp: false },
  payment: { razorpayKeyId: '', enableCod: true, enableOnlinePayments: true, currency: 'INR' },
  cod: { minOrderValue: 1000, maxOrderValue: 3000 },
  email: { fromName: '', fromEmail: '', smtpHost: '', smtpPort: 587, smtpUser: '', smtpPass: '' },
  tax: { gstRate: 18, enableTax: true, taxCalculationMethod: 'inclusive', hsnRequired: false },
};

// The server stores every value as text, so "false" must not be treated as true.
const BOOLEAN_KEYS = {
  security: ['requireOtp'],
  payment: ['enableCod', 'enableOnlinePayments'],
  tax: ['enableTax', 'hsnRequired'],
};
const NUMBER_KEYS = {
  security: ['minPasswordLength', 'maxLoginAttempts', 'sessionTimeout'],
  cod: ['minOrderValue', 'maxOrderValue'],
  email: ['smtpPort'],
  tax: ['gstRate'],
};
const toBool = (v) => v === true || String(v).trim().toLowerCase() === 'true';

const normalise = (section, values) => {
  const out = { ...values };
  (BOOLEAN_KEYS[section] || []).forEach(k => { out[k] = toBool(out[k]); });
  (NUMBER_KEYS[section] || []).forEach(k => {
    const n = Number(out[k]);
    out[k] = out[k] === '' || Number.isNaN(n) ? initialSettings[section][k] : n;
  });
  return out;
};

// Returns an error message, or '' when the section is fine to save.
const validateSection = (section, v) => {
  for (const k of NUMBER_KEYS[section] || []) {
    const n = Number(v[k]);
    if (v[k] === '' || Number.isNaN(n) || n < 0) return 'Enter a number, 0 or more, in every number field.';
  }
  if (section === 'cod' && Number(v.maxOrderValue) > 0 && Number(v.minOrderValue) > Number(v.maxOrderValue)) {
    return 'The cash on delivery minimum cannot be more than the maximum.';
  }
  if (section === 'tax' && Number(v.gstRate) > 100) return 'GST rate cannot be more than 100%.';
  if (section === 'security' && Number(v.minPasswordLength) < 6) return 'Minimum password length must be at least 6.';
  if (section === 'email' && v.fromEmail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.fromEmail)) return 'From Email is not a valid email address.';
  if (section === 'store' && v.contactEmail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.contactEmail)) return 'Contact Email is not a valid email address.';
  return '';
};

// An on/off switch. Read-only (greyed, not clickable) until the section is being edited.
const ToggleSwitch = ({ checked, onChange, disabled, label }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      style={{
        position: 'relative',
        width: '44px',
        height: '24px',
        padding: 0,
        border: 'none',
        borderRadius: '999px',
        background: checked ? '#0f766e' : '#cbd5e1',
        opacity: disabled ? 0.6 : 1,
        cursor: disabled ? 'default' : 'pointer',
        transition: 'background 0.15s',
        flexShrink: 0,
      }}
    >
      <span
        style={{
          position: 'absolute',
          top: '3px',
          left: checked ? '23px' : '3px',
          width: '18px',
          height: '18px',
          borderRadius: '50%',
          background: '#fff',
          boxShadow: '0 1px 2px rgba(0,0,0,0.3)',
          transition: 'left 0.15s',
        }}
      />
    </button>
    <span style={{ fontSize: '0.9rem', color: checked ? '#0f766e' : '#64748b', fontWeight: 500 }}>
      {checked ? 'Enabled' : 'Disabled'}
    </span>
  </div>
);

const SettingField = ({ label, value, onChange, type = 'text', editing, options, placeholder }) => {
  if (type === 'boolean') {
    return (
      <div className="admin-form-group">
        <label className="admin-form-label">{label}</label>
        <div style={{ padding: editing ? 0 : '4px 0' }}>
          <ToggleSwitch checked={!!value} onChange={onChange} disabled={!editing} label={label} />
        </div>
      </div>
    );
  }

  if (!editing) {
    return (
      <div className="admin-form-group">
        <label className="admin-form-label">{label}</label>
        <div style={{ padding: '8px 0', fontSize: '0.9rem', color: '#1f2937' }}>
          {type === 'password' ? '••••••••' : value || '-'}
        </div>
      </div>
    );
  }

  return (
    <div className="admin-form-group">
      <label className="admin-form-label">{label}</label>
      {type === 'select' ? (
        <select className="admin-form-select" value={value} onChange={e => onChange(e.target.value)}>
          {options?.map(o => <option key={o.value || o} value={o.value || o}>{o.label || o}</option>)}
        </select>
      ) : type === 'textarea' ? (
        <textarea className="admin-form-textarea" value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} />
      ) : (
        <input className="admin-form-input" type={type} value={value} min={type === 'number' ? 0 : undefined} onChange={e => onChange(e.target.value)} placeholder={placeholder} />
      )}
    </div>
  );
};

const SettingsSection = ({ title, fields, children, editing, setEditing, onSave, saving }) => (
  <div className="admin-chart-card" style={{ marginBottom: '24px' }}>
    <div className="admin-chart-header">
      <div className="admin-chart-title">{title}</div>
      <div style={{ display: 'flex', gap: '8px' }}>
        {editing ? (
          <>
            <button className="admin-btn admin-btn-secondary admin-btn-sm" onClick={() => setEditing(false)}>Cancel</button>
            <button className="admin-btn admin-btn-primary admin-btn-sm" onClick={onSave} disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
          </>
        ) : (
          <button className="admin-btn admin-btn-primary admin-btn-sm" onClick={() => setEditing(true)}>Edit</button>
        )}
      </div>
    </div>
    {fields}
    {children}
  </div>
);

const PlatformSettings = () => {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState('store');
  const [settings, setSettings] = useState(initialSettings);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [snapshot, setSnapshot] = useState({});
  const [editState, setEditState] = useState({ store: false, security: false, payment: false, cod: false, email: false, tax: false });

  const flatToNested = (flat) => {
    if (!flat || !Array.isArray(flat)) return flatToNested([]);
    const grouped = { store: {}, security: {}, payment: {}, cod: {}, email: {}, tax: {} };
    flat.forEach(item => {
      const key = item.settingKey || item.key || '';
      const value = item.settingValue || item.value || '';
      const cat = item.category ? item.category.toLowerCase() : '';
      if (key.startsWith('store.') || cat === 'store' || cat === 'general') {
        const k = key.replace('store.', '');
        if (k) grouped.store[k] = value;
      } else if (key.startsWith('security.') || cat === 'security') {
        const k = key.replace('security.', '');
        if (k) grouped.security[k] = value;
      } else if (key.startsWith('payment.') || cat === 'payment') {
        const k = key.replace('payment.', '');
        if (k) grouped.payment[k] = value;
      } else if (key.startsWith('cod.') || cat === 'cod') {
        const k = key.replace('cod.', '');
        if (k) grouped.cod[k] = value;
      } else if (key.startsWith('email.') || cat === 'email') {
        const k = key.replace('email.', '');
        if (k) grouped.email[k] = value;
      } else if (key.startsWith('tax.') || cat === 'tax' || cat === 'gst') {
        const k = key.replace('tax.', '');
        if (k) grouped.tax[k] = value;
      }
    });
    return {
      store: normalise('store', { ...initialSettings.store, ...grouped.store }),
      security: normalise('security', { ...initialSettings.security, ...grouped.security }),
      payment: normalise('payment', { ...initialSettings.payment, ...grouped.payment }),
      cod: normalise('cod', { ...initialSettings.cod, ...grouped.cod }),
      email: normalise('email', { ...initialSettings.email, ...grouped.email }),
      tax: normalise('tax', { ...initialSettings.tax, ...grouped.tax }),
    };
  };

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const res = await api.get('/api/super-admin/settings');
        setSettings(flatToNested(res.data));
      } catch (err) {
        setError(err.response?.data?.message || 'Failed to load settings');
      } finally {
        setLoading(false);
      }
    };
    fetchSettings();
  }, []);

  const updateSetting = (section, key) => (value) => {
    setSettings(prev => ({ ...prev, [section]: { ...prev[section], [key]: value } }));
  };

  const startEditing = (section, on) => {
    if (on) {
      setSnapshot(prev => ({ ...prev, [section]: settings[section] }));
    } else {
      // Cancel: put back what was saved, so abandoned edits do not linger on screen.
      setSettings(prev => ({ ...prev, [section]: snapshot[section] || prev[section] }));
      setError('');
    }
    setEditState(prev => ({ ...prev, [section]: on }));
  };

  const handleSave = async (section) => {
    const problem = validateSection(section, settings[section]);
    if (problem) {
      setError(problem);
      return;
    }
    setSaving(true);
    setError('');
    try {
      const sectionData = settings[section];
      const flat = {};
      Object.entries(sectionData).forEach(([key, value]) => {
        flat[`${section}.${key}`] = String(value);
      });
      const res = await api.put('/api/super-admin/settings', flat);
      setSettings(flatToNested(res.data));
      setEditState(prev => ({ ...prev, [section]: false }));
      showToast(`${SECTION_NAMES[section]} settings saved`, 'success');
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Failed to save settings';
      setError(msg);
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>Platform Settings</h1>
        <div className="admin-skeleton-row"><div className="admin-skeleton-card" /></div>
        <div className="admin-skeleton-row"><div className="admin-skeleton-card" /></div>
      </div>
    );
  }

  const renderStoreSettings = () => (
    <SettingsSection title="Store Settings" editing={editState.store} setEditing={(v) => startEditing('store', v)} onSave={() => handleSave('store')} saving={saving}>
      <SettingField label="Store Name" value={settings.store.name} onChange={updateSetting('store', 'name')} editing={editState.store} />
      <SettingField label="Description" value={settings.store.description} onChange={updateSetting('store', 'description')} editing={editState.store} type="textarea" />
      <div className="admin-form-row">
        <SettingField label="Currency" value={settings.store.currency} onChange={updateSetting('store', 'currency')} editing={editState.store} type="select" options={['INR', 'USD', 'EUR', 'GBP']} />
        <SettingField label="Timezone" value={settings.store.timezone} onChange={updateSetting('store', 'timezone')} editing={editState.store} type="select" options={['Asia/Kolkata', 'UTC', 'America/New_York', 'Europe/London']} />
      </div>
      <div className="admin-form-row">
        <SettingField label="Contact Email" value={settings.store.contactEmail} onChange={updateSetting('store', 'contactEmail')} editing={editState.store} type="email" />
        <SettingField label="Phone" value={settings.store.phone} onChange={updateSetting('store', 'phone')} editing={editState.store} />
      </div>
      <SettingField label="Address" value={settings.store.address} onChange={updateSetting('store', 'address')} editing={editState.store} type="textarea" />
    </SettingsSection>
  );

  const renderSecuritySettings = () => (
    <SettingsSection title="Security Settings" editing={editState.security} setEditing={(v) => startEditing('security', v)} onSave={() => handleSave('security')} saving={saving}>
      <div className="admin-form-row">
        <SettingField label="Min Password Length" value={settings.security.minPasswordLength} onChange={updateSetting('security', 'minPasswordLength')} editing={editState.security} type="number" />
        <SettingField label="Max Login Attempts" value={settings.security.maxLoginAttempts} onChange={updateSetting('security', 'maxLoginAttempts')} editing={editState.security} type="number" />
      </div>
      <div className="admin-form-row">
        <SettingField label="Session Timeout (min)" value={settings.security.sessionTimeout} onChange={updateSetting('security', 'sessionTimeout')} editing={editState.security} type="number" />
        <SettingField label="Require OTP for Admin Login" value={settings.security.requireOtp} onChange={updateSetting('security', 'requireOtp')} editing={editState.security} type="boolean" />
      </div>
    </SettingsSection>
  );

  const renderPaymentSettings = () => (
    <SettingsSection title="Payment Configuration" editing={editState.payment} setEditing={(v) => startEditing('payment', v)} onSave={() => handleSave('payment')} saving={saving}>
      <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 12px' }}>
        Switch cash on delivery or online payment off to stop offering it on the website, the app and the server straight after saving.
        At least one of them must stay on. The cash on delivery order range is on the Cash on Delivery tab.
      </p>
      <SettingField label="Razorpay Key ID" value={settings.payment.razorpayKeyId} onChange={updateSetting('payment', 'razorpayKeyId')} editing={editState.payment} type={editState.payment ? 'text' : 'password'} />
      <div className="admin-form-row">
        <SettingField label="Enable COD" value={settings.payment.enableCod} onChange={updateSetting('payment', 'enableCod')} editing={editState.payment} type="boolean" />
        <SettingField label="Enable Online Payments" value={settings.payment.enableOnlinePayments} onChange={updateSetting('payment', 'enableOnlinePayments')} editing={editState.payment} type="boolean" />
      </div>
      <SettingField label="Currency" value={settings.payment.currency} onChange={updateSetting('payment', 'currency')} editing={editState.payment} type="select" options={['INR', 'USD', 'EUR', 'GBP']} />
    </SettingsSection>
  );

  const renderCodSettings = () => (
    <SettingsSection title="Cash on Delivery Limits" editing={editState.cod} setEditing={(v) => startEditing('cod', v)} onSave={() => handleSave('cod')} saving={saving}>
      <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 12px' }}>
        Cash on delivery is offered only when the order total is between these two amounts (inclusive); other orders must be paid online.
        Enter 0 as the maximum for no upper limit. Applies to the website, the app and the server straight after saving.
      </p>
      <div className="admin-form-row">
        <SettingField label="Minimum order value (₹)" value={settings.cod.minOrderValue} onChange={updateSetting('cod', 'minOrderValue')} editing={editState.cod} type="number" />
        <SettingField label="Maximum order value (₹)" value={settings.cod.maxOrderValue} onChange={updateSetting('cod', 'maxOrderValue')} editing={editState.cod} type="number" />
      </div>
    </SettingsSection>
  );

  const renderEmailSettings = () => (
    <SettingsSection title="Email Configuration" editing={editState.email} setEditing={(v) => startEditing('email', v)} onSave={() => handleSave('email')} saving={saving}>
      <div className="admin-form-row">
        <SettingField label="From Name" value={settings.email.fromName} onChange={updateSetting('email', 'fromName')} editing={editState.email} />
        <SettingField label="From Email" value={settings.email.fromEmail} onChange={updateSetting('email', 'fromEmail')} editing={editState.email} type="email" />
      </div>
      <div className="admin-form-row">
        <SettingField label="SMTP Host" value={settings.email.smtpHost} onChange={updateSetting('email', 'smtpHost')} editing={editState.email} />
        <SettingField label="SMTP Port" value={settings.email.smtpPort} onChange={updateSetting('email', 'smtpPort')} editing={editState.email} type="number" />
      </div>
      <div className="admin-form-row">
        <SettingField label="SMTP Username" value={settings.email.smtpUser} onChange={updateSetting('email', 'smtpUser')} editing={editState.email} />
        <SettingField label="SMTP Password" value={settings.email.smtpPass} onChange={updateSetting('email', 'smtpPass')} editing={editState.email} type={editState.email ? 'text' : 'password'} />
      </div>
    </SettingsSection>
  );

  const renderTaxSettings = () => (
    <SettingsSection title="Tax / GST Configuration" editing={editState.tax} setEditing={(v) => startEditing('tax', v)} onSave={() => handleSave('tax')} saving={saving}>
      <div className="admin-form-row">
        <SettingField label="GST Rate (%)" value={settings.tax.gstRate} onChange={updateSetting('tax', 'gstRate')} editing={editState.tax} type="number" />
        <SettingField label="Enable Tax" value={settings.tax.enableTax} onChange={updateSetting('tax', 'enableTax')} editing={editState.tax} type="boolean" />
      </div>
      <div className="admin-form-row">
        <SettingField label="Tax Calculation Method" value={settings.tax.taxCalculationMethod} onChange={updateSetting('tax', 'taxCalculationMethod')} editing={editState.tax} type="select" options={['inclusive', 'exclusive']} />
        <SettingField label="Require HSN Code" value={settings.tax.hsnRequired} onChange={updateSetting('tax', 'hsnRequired')} editing={editState.tax} type="boolean" />
      </div>
    </SettingsSection>
  );

  const tabContent = {
    store: renderStoreSettings(),
    security: renderSecuritySettings(),
    payment: renderPaymentSettings(),
    cod: renderCodSettings(),
    email: renderEmailSettings(),
    tax: renderTaxSettings(),
  };

  return (
    <div>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>Platform Settings</h1>

      {error && <div style={{ background: '#fef2f2', color: '#dc2626', padding: '8px 16px', borderRadius: '8px', marginBottom: '16px', fontSize: '0.85rem' }}>{error}</div>}

      <div style={{ display: 'flex', gap: '8px', marginBottom: '24px', borderBottom: '1px solid #e2e8f0', paddingBottom: '0', flexWrap: 'wrap' }}>
        {TABS.map(tab => (
          <button
            key={tab.id}
            onClick={() => { setActiveTab(tab.id); setError(''); }}
            style={{
              padding: '10px 20px',
              background: activeTab === tab.id ? '#0f172a' : 'transparent',
              color: activeTab === tab.id ? '#fff' : '#64748b',
              border: 'none',
              borderBottom: activeTab === tab.id ? '2px solid #f59e0b' : '2px solid transparent',
              borderRadius: '8px 8px 0 0',
              cursor: 'pointer',
              fontSize: '0.85rem',
              fontWeight: activeTab === tab.id ? 600 : 400,
              transition: 'all 0.15s',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            {tab.icon} {tab.label}
          </button>
        ))}
      </div>

      {tabContent[activeTab]}
    </div>
  );
};

export default PlatformSettings;
