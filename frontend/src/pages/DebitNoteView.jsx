import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, Printer, Download, Loader } from "lucide-react";
import api from "../api/axios";

const GST_DEBIT_NOTE_STYLES = `
  .giv-page { max-width: 900px; margin: 0 auto; padding: 1.5rem; }
  .giv-toolbar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; }
  .giv-back { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.45rem 0.9rem; border: 1.5px solid #e2e8f0; border-radius: 8px; background: #fff; color: #475569; font-size: 0.85rem; font-weight: 500; cursor: pointer; text-decoration: none; }
  .giv-back:hover { background: #f8fafc; }
  .giv-btn { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.45rem 0.9rem; border-radius: 8px; font-size: 0.85rem; font-weight: 600; border: none; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
  .giv-btn-primary { background: #0E5C5C; color: #fff; }
  .giv-btn-primary:hover { background: #0a4a4a; }
  .giv-btn-outline { background: #fff; color: #0E5C5C; border: 1.5px solid #0E5C5C; }
  .giv-btn-outline:hover { background: #f0fdf4; }

  .giv-invoice { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); overflow: hidden; }
  .giv-header { padding: 1.5rem 2rem; border-bottom: 2px solid #0E5C5C; display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
  .giv-header-left { display: flex; align-items: center; gap: 1rem; }
  .giv-header-left img { height: 2.5rem; width: auto; }
  .giv-brand-name { font-size: 0.95rem; font-weight: 700; color: #0E5C5C; line-height: 1.2; }
  .giv-brand-tagline { font-size: 0.68rem; font-weight: 600; color: #C8A24B; margin-bottom: 0.25rem; }
  .giv-header-left h1 { font-size: 1.1rem; font-weight: 700; color: #0f172a; margin: 0; }
  .giv-header-right { text-align: right; }
  .giv-header-right .giv-inv-number { font-size: 1rem; font-weight: 700; color: #0E5C5C; }
  .giv-header-right .giv-inv-label { font-size: 0.78rem; color: #64748b; margin-bottom: 0.15rem; }

  .giv-body { padding: 1.5rem 2rem; }
  .giv-parties { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-bottom: 1.5rem; }
  .giv-party-box { padding: 1rem; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; }
  .giv-party-box h3 { font-size: 0.8rem; font-weight: 700; color: #0E5C5C; text-transform: uppercase; letter-spacing: 0.3px; margin: 0 0 0.5rem; padding-bottom: 0.35rem; border-bottom: 1px solid #e2e8f0; }
  .giv-party-box .giv-party-name { font-size: 0.95rem; font-weight: 600; color: #0f172a; }
  .giv-party-box .giv-party-detail { font-size: 0.82rem; color: #475569; line-height: 1.5; }
  .giv-party-box .giv-party-gstin { font-size: 0.8rem; font-family: monospace; font-weight: 600; color: #0f172a; margin-top: 0.35rem; }

  .giv-reason { margin-bottom: 1.5rem; padding: 0.75rem 1rem; border: 1px solid #e2e8f0; border-radius: 8px; background: #fffbeb; font-size: 0.85rem; color: #475569; }
  .giv-reason strong { color: #0E5C5C; }

  .giv-identifiers { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.75rem; margin-bottom: 1.5rem; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 0.75rem 1rem; }
  .giv-id-label { font-size: 0.7rem; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.3px; }
  .giv-id-value { font-size: 0.85rem; font-weight: 600; color: #0f172a; margin-top: 0.1rem; }

  .giv-items { margin-bottom: 1.5rem; overflow-x: auto; }
  .giv-items table { width: 100%; border-collapse: collapse; font-size: 0.82rem; }
  .giv-items th { background: #0E5C5C; color: #fff; padding: 0.6rem 0.75rem; text-align: left; font-weight: 600; white-space: nowrap; font-size: 0.78rem; }
  .giv-items td { padding: 0.55rem 0.75rem; border-bottom: 1px solid #f1f5f9; color: #1e293b; }
  .giv-items tr:nth-child(even) td { background: #f8fafc; }
  .giv-items .giv-hsn { font-family: monospace; font-size: 0.78rem; color: #64748b; }
  .giv-items .giv-amt { text-align: right; font-family: monospace; white-space: nowrap; }
  .giv-items .giv-amt-total { font-weight: 700; color: #0E5C5C; }

  .giv-summary { display: flex; justify-content: flex-end; margin-bottom: 1.5rem; }
  .giv-summary-table { width: 350px; border-collapse: collapse; font-size: 0.85rem; }
  .giv-summary-table td { padding: 0.4rem 0.75rem; border-bottom: 1px solid #f1f5f9; }
  .giv-summary-table td:first-child { color: #64748b; }
  .giv-summary-table td:last-child { text-align: right; font-weight: 600; font-family: monospace; }
  .giv-summary-table .giv-sum-total { font-size: 1rem; font-weight: 800; color: #0E5C5C; border-top: 2px solid #0E5C5C; }
  .giv-summary-table .giv-sum-total td { padding: 0.6rem 0.75rem; }

  .giv-amount-words { font-size: 0.85rem; color: #475569; margin-bottom: 1.5rem; padding: 0.75rem 1rem; border: 1px solid #e2e8f0; border-radius: 8px; background: #fffbeb; }
  .giv-amount-words strong { color: #0E5C5C; }

  .giv-declaration { font-size: 0.78rem; color: #64748b; line-height: 1.6; padding: 0.75rem 1rem; border-top: 1px solid #e2e8f0; }
  .giv-footer { text-align: center; padding: 1rem 2rem; border-top: 1px solid #e2e8f0; font-size: 0.75rem; color: #94a3b8; }

  @media print {
    .giv-toolbar, .giv-back { display: none !important; }
    .giv-page { padding: 0; }
    .giv-invoice { border: none; box-shadow: none; border-radius: 0; }
    .giv-body { padding: 1rem 1.5rem; }
  }
  @media (max-width: 768px) {
    .giv-parties { grid-template-columns: 1fr; }
    .giv-summary-table { width: 100%; }
  }
`;

const numberToWords = (num) => {
  if (!num || isNaN(num)) return "Zero";
  const a = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
  const b = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];
  const fn = (n) => {
    if (n < 20) return a[n];
    if (n < 100) return b[Math.floor(n / 10)] + (n % 10 ? " " + a[n % 10] : "");
    if (n < 1000) return a[Math.floor(n / 100)] + " Hundred" + (n % 100 ? " " + fn(n % 100) : "");
    if (n < 100000) return fn(Math.floor(n / 1000)) + " Thousand" + (n % 1000 ? " " + fn(n % 1000) : "");
    if (n < 10000000) return fn(Math.floor(n / 100000)) + " Lakh" + (n % 100000 ? " " + fn(n % 100000) : "");
    return fn(Math.floor(n / 10000000)) + " Crore" + (n % 10000000 ? " " + fn(n % 10000000) : "");
  };
  const whole = Math.floor(num);
  const decimal = Math.round((num - whole) * 100);
  let result = fn(whole) + " Rupees";
  if (decimal > 0) result += " and " + fn(decimal) + " Paise";
  return result + " Only";
};

const DebitNoteView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [debitNote, setDebitNote] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [pdfLoading, setPdfLoading] = useState(false);

  const handleDownloadPdf = async () => {
    setPdfLoading(true);
    try {
      const res = await api.get(`/api/gst/debitnote/${id}/pdf`, { responseType: "blob" });
      const blob = new Blob([res.data], { type: "application/pdf" });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `debit-note-${id}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      if (err.response?.status === 403) setError("Access denied: You do not have permission to download this debit note.");
      else setError("Failed to download PDF.");
    }
    setPdfLoading(false);
  };

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await api.get(`/api/gst/debitnote/${id}`);
        setDebitNote(res.data.debitNote);
      } catch (err) {
        if (err.response?.status === 403) setError("Access denied: You do not have permission to view this debit note.");
        else setError("Failed to load debit note");
      }
      setLoading(false);
    };
    fetchData();
  }, [id]);

  if (loading) return <div className="giv-page" style={{ textAlign: "center", padding: "3rem", color: "#94a3b8" }}>Loading debit note...</div>;
  if (error) return <div className="giv-page" style={{ textAlign: "center", padding: "3rem", color: "#dc2626" }}>{error}</div>;
  if (!debitNote) return <div className="giv-page" style={{ textAlign: "center", padding: "3rem", color: "#94a3b8" }}>Debit note not found.</div>;

  const dn = debitNote;

  return (
    <>
      <style>{GST_DEBIT_NOTE_STYLES}</style>
      <div className="giv-page">
        <div className="giv-toolbar">
          <button className="giv-back" onClick={() => navigate(-1)}><ArrowLeft size={16} /> Back</button>
          <button className="giv-btn giv-btn-primary" onClick={() => window.print()}><Printer size={16} /> Print</button>
          <button className="giv-btn giv-btn-outline" onClick={handleDownloadPdf} disabled={pdfLoading}>
            {pdfLoading ? <Loader size={16} /> : <Download size={16} />} {pdfLoading ? "Downloading..." : "Download PDF"}
          </button>
        </div>

        <div className="giv-invoice" id="debit-note-print">
          <div className="giv-header">
            <div className="giv-header-left">
              <img src="/images/logo.jpg" alt="Cauvery Store" />
              <div>
                <div className="giv-brand-name">Cauvery Store</div>
                <div className="giv-brand-tagline">Everyday Essentials, Delivered</div>
                <h1>Debit Note</h1>
                <div style={{ fontSize: "0.78rem", color: "#64748b" }}>
                  Original for Recipient{dn.isInterState ? " (Inter-State)" : " (Intra-State)"}
                </div>
              </div>
            </div>
            <div className="giv-header-right">
              <div className="giv-inv-label">Debit Note No.</div>
              <div className="giv-inv-number">{dn.debitNoteNumber}</div>
              <div className="giv-inv-label" style={{ marginTop: "0.3rem" }}>Date</div>
              <div style={{ fontSize: "0.88rem", fontWeight: 600 }}>{dn.debitNoteDate}</div>
              {dn.originalInvoiceNumber && (
                <>
                  <div className="giv-inv-label" style={{ marginTop: "0.3rem" }}>Against Invoice</div>
                  <div style={{ fontSize: "0.82rem", fontFamily: "monospace", fontWeight: 600 }}>{dn.originalInvoiceNumber}</div>
                </>
              )}
            </div>
          </div>

          <div className="giv-body">
            <div className="giv-parties">
              <div className="giv-party-box">
                <h3>Seller (Supplier)</h3>
                <div className="giv-party-name">{dn.sellerLegalName || "Cauvery Store"}</div>
                <div className="giv-party-detail">{dn.sellerAddress || ""}</div>
                <div className="giv-party-gstin">GSTIN: {dn.sellerGstin}</div>
              </div>
              <div className="giv-party-box">
                <h3>Buyer (Recipient)</h3>
                <div className="giv-party-name">{dn.buyerName}</div>
                <div className="giv-party-detail">{dn.buyerAddress}</div>
                <div className="giv-party-gstin">{dn.buyerGstin === "URP" ? "GSTIN: URP (Unregistered Person)" : "GSTIN: " + dn.buyerGstin}</div>
              </div>
            </div>

            {dn.reason && (
              <div className="giv-reason">
                <strong>Reason:</strong> {dn.reason}
              </div>
            )}

            <div className="giv-identifiers">
              <div className="giv-id-item"><div className="giv-id-label">Place of Supply</div><div className="giv-id-value">{dn.placeOfSupply || "-"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Supply Type</div><div className="giv-id-value">{dn.isInterState ? "Inter-State (IGST)" : "Intra-State (CGST+SGST)"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Invoice Type</div><div className="giv-id-value">{dn.invoiceType || "B2C"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Order</div><div className="giv-id-value">#{dn.orderId}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Reference</div><div className="giv-id-value">{dn.referenceType || "-"}</div></div>
              <div className="giv-id-item"><div className="giv-id-label">Status</div><div className="giv-id-value">{dn.status}</div></div>
            </div>

            <div className="giv-items">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>HSN/SAC</th>
                    <th>Description</th>
                    <th style={{ textAlign: "right" }}>Qty</th>
                    <th style={{ textAlign: "right" }}>Unit Price</th>
                    <th style={{ textAlign: "right" }}>Taxable Value</th>
                    {!dn.isInterState && <><th style={{ textAlign: "right" }}>CGST</th><th style={{ textAlign: "right" }}>SGST</th></>}
                    {dn.isInterState && <th style={{ textAlign: "right" }}>IGST</th>}
                    <th style={{ textAlign: "right" }}>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {(dn.items || []).map((item, i) => (
                    <tr key={i}>
                      <td>{i + 1}</td>
                      <td className="giv-hsn">{item.hsnCode || "-"}</td>
                      <td>{item.productName}</td>
                      <td className="giv-amt">{item.quantity}</td>
                      <td className="giv-amt">&#8377;{(item.unitPrice || 0).toFixed(2)}</td>
                      <td className="giv-amt">&#8377;{(item.taxableValue || 0).toFixed(2)}</td>
                      {!dn.isInterState && <><td className="giv-amt">{(item.cgstRate || 0)}%<br />&#8377;{(item.cgstAmount || 0).toFixed(2)}</td><td className="giv-amt">{(item.sgstRate || 0)}%<br />&#8377;{(item.sgstAmount || 0).toFixed(2)}</td></>}
                      {dn.isInterState && <td className="giv-amt">{(item.igstRate || 0)}%<br />&#8377;{(item.igstAmount || 0).toFixed(2)}</td>}
                      <td className="giv-amt giv-amt-total">&#8377;{(item.totalAmount || 0).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="giv-summary">
              <table className="giv-summary-table">
                <tbody>
                  <tr><td>Taxable Amount</td><td>&#8377;{(dn.taxableAmount || 0).toFixed(2)}</td></tr>
                  {!dn.isInterState && <><tr><td>CGST</td><td style={{ color: "#2563eb" }}>&#8377;{(dn.cgstAmount || 0).toFixed(2)}</td></tr><tr><td>SGST</td><td style={{ color: "#7c3aed" }}>&#8377;{(dn.sgstAmount || 0).toFixed(2)}</td></tr></>}
                  {dn.isInterState && <tr><td>IGST</td><td style={{ color: "#d97706" }}>&#8377;{(dn.igstAmount || 0).toFixed(2)}</td></tr>}
                  <tr><td>Total Tax</td><td>&#8377;{(dn.totalTax || 0).toFixed(2)}</td></tr>
                  {dn.tcsAmount > 0 && <tr><td>TCS</td><td style={{ color: "#dc2626" }}>&#8377;{(dn.tcsAmount || 0).toFixed(2)}</td></tr>}
                  <tr className="giv-sum-total"><td>Total Amount Payable</td><td>&#8377;{(dn.totalAmount || 0).toFixed(2)}</td></tr>
                </tbody>
              </table>
            </div>

            <div className="giv-amount-words">
              <strong>Amount in Words:</strong> {numberToWords(dn.totalAmount || 0)}
            </div>

            <div className="giv-declaration">
              <strong>Declaration:</strong> This debit note documents an additional tax charge on the original supply and is reported in GSTR-1 / GSTR-3B for the period in which it is issued. It is a system-generated document and does not require a physical signature.
            </div>
          </div>

          <div className="giv-footer">
            This is a computer-generated GST debit note | {dn.debitNoteNumber} | Generated on {new Date().toLocaleDateString()}
          </div>
        </div>
      </div>
    </>
  );
};

export default DebitNoteView;
