import React, { useState, useEffect } from 'react';
import { Store, Lock, CreditCard, Mail, Receipt } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../admin/context/ToastContext';

const TABS = [
  { id: 'store', label: 'Store Settings', icon: <Store size={18} /> },
  { id: 'security', label: 'Security', icon: <Lock size={18} /> },
  { id: 'payment', label: 'Payment', icon: <CreditCard size={18} /> },
  { id: 'email', label: 'Email', icon: <Mail size={18} /> },
  { id: 'tax', label: 'Tax / GST', icon: <Receipt size={18} /> },
];

const initialSettings = {
  store: { name: 'Cauvery Store', description: '', currency: 'INR', timezone: 'Asia/Kolkata', contactEmail: '', phone: '', address: '' },
  security: { minPasswordLength: 8, maxLoginAttempts: 5, sessionTimeout: 60, requireOtp: false },
  payment: { razorpayKeyId: '', enableCod: true, enableOnlinePayments: true, currency: 'INR' },
  email: { fromName: '', fromEmail: '', smtpHost: '', smtpPort: 587, smtpUser: '', smtpPass: '' },
  tax: { gstRate: 18, enableTax: true, taxCalculationMethod: 'inclusive', hsnRequired: false },
};

const SettingField = ({ label, value, onChange, type = 'text', editing, options, placeholder }) => {
  if (!editing) {
    return (
      <div className="admin-form-group">
        <label className="admin-form-label">{label}</label>
        <div style={{ padding: '8px 0', fontSize: '0.9rem', color: '#1f2937' }}>
          {type === 'boolean' ? (value ? 'Yes' : 'No') : type === 'password' ? '••••••••' : value || '-'}
        </div>
      </div>
    );
  }

  return (
    <div className="admin-form-group">
      <label className="admin-form-label">{label}</label>
      {type === 'boolean' ? (
        <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '0.9rem' }}>
          <input type="checkbox" checked={value} onChange={e => onChange(e.target.checked)} style={{ width: '18px', height: '18px', accentColor: '#f59e0b' }} />
          {value ? 'Enabled' : 'Disabled'}
        </label>
      ) : type === 'select' ? (
        <select className="admin-form-select" value={value} onChange={e => onChange(e.target.value)}>
          {options?.map(o => <option key={o.value || o} value={o.value || o}>{o.label || o}</option>)}
        </select>
      ) : type === 'textarea' ? (
        <textarea className="admin-form-textarea" value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} />
      ) : (
        <input className="admin-form-input" type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} />
      )}
    </div>
  );
};

const SettingsSection = ({ title, fields, editing, setEditing, onSave, saving }) => (
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
  </div>
);

const PlatformSettings = () => {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState('store');
  const [settings, setSettings] = useState(initialSettings);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [editState, setEditState] = useState({ store: false, security: false, payment: false, email: false, tax: false });
  const [testEmailResult, setTestEmailResult] = useState('');

  const flatToNested = (flat) => {
    if (!flat || !Array.isArray(flat)) return initialSettings;
    const grouped = { store: {}, security: {}, payment: {}, email: {}, tax: {} };
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
      } else if (key.startsWith('email.') || cat === 'email') {
        const k = key.replace('email.', '');
        if (k) grouped.email[k] = value;
      } else if (key.startsWith('tax.') || cat === 'tax' || cat === 'gst') {
        const k = key.replace('tax.', '');
        if (k) grouped.tax[k] = value;
      }
    });
    return {
      store: { ...initialSettings.store, ...grouped.store },
      security: { ...initialSettings.security, ...grouped.security },
      payment: { ...initialSettings.payment, ...grouped.payment },
      email: { ...initialSettings.email, ...grouped.email },
      tax: { ...initialSettings.tax, ...grouped.tax },
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

  const handleSave = async (section) => {
    setSaving(true);
    setError('');
    try {
      const sectionData = settings[section];
      const flat = {};
      Object.entries(sectionData).forEach(([key, value]) => {
        flat[`${section}.${key}`] = String(value);
      });
      await api.put('/api/super-admin/settings', flat);
      setEditState(prev => ({ ...prev, [section]: false }));
      showToast(`${section} settings saved`, 'success');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save settings');
      showToast('Failed to save settings', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleTestEmail = async () => {
    setTestEmailResult('');
    try {
      await api.post('/api/super-admin/settings/email/test', { to: settings.email.fromEmail });
      setTestEmailResult('Test email sent successfully!');
    } catch (err) {
      setTestEmailResult(err.response?.data?.message || 'Failed to send test email');
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
    <SettingsSection title="Store Settings" editing={editState.store} setEditing={(v) => setEditState(p => ({...p, store: v}))} onSave={() => handleSave('store')} saving={saving}>
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
    <SettingsSection title="Security Settings" editing={editState.security} setEditing={(v) => setEditState(p => ({...p, security: v}))} onSave={() => handleSave('security')} saving={saving}>
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
    <SettingsSection title="Payment Configuration" editing={editState.payment} setEditing={(v) => setEditState(p => ({...p, payment: v}))} onSave={() => handleSave('payment')} saving={saving}>
      <SettingField label="Razorpay Key ID" value={settings.payment.razorpayKeyId} onChange={updateSetting('payment', 'razorpayKeyId')} editing={editState.payment} type={editState.payment ? 'text' : 'password'} />
      <div className="admin-form-row">
        <SettingField label="Enable COD" value={settings.payment.enableCod} onChange={updateSetting('payment', 'enableCod')} editing={editState.payment} type="boolean" />
        <SettingField label="Enable Online Payments" value={settings.payment.enableOnlinePayments} onChange={updateSetting('payment', 'enableOnlinePayments')} editing={editState.payment} type="boolean" />
      </div>
      <SettingField label="Currency" value={settings.payment.currency} onChange={updateSetting('payment', 'currency')} editing={editState.payment} type="select" options={['INR', 'USD', 'EUR', 'GBP']} />
    </SettingsSection>
  );

  const renderEmailSettings = () => (
    <SettingsSection title="Email Configuration" editing={editState.email} setEditing={(v) => setEditState(p => ({...p, email: v}))} onSave={() => handleSave('email')} saving={saving}>
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
      {editState.email && (
        <div style={{ marginTop: '12px' }}>
          <button className="admin-btn admin-btn-secondary admin-btn-sm" onClick={handleTestEmail}>Send Test Email</button>
          {testEmailResult && (
            <span style={{ marginLeft: '12px', fontSize: '0.85rem', color: testEmailResult.includes('success') ? '#16a34a' : '#dc2626' }}>
              {testEmailResult}
            </span>
          )}
        </div>
      )}
    </SettingsSection>
  );

  const renderTaxSettings = () => (
    <SettingsSection title="Tax / GST Configuration" editing={editState.tax} setEditing={(v) => setEditState(p => ({...p, tax: v}))} onSave={() => handleSave('tax')} saving={saving}>
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
            onClick={() => setActiveTab(tab.id)}
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
