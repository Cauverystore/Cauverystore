import React, { useState, useEffect, useCallback, useRef } from "react";
import api from "../../api/axios";

/**
 * Picks an HSN code from the official GSTN master.
 *
 * Classifying goods is the seller's judgement - only they know what the thing actually is -
 * but a free-text box made getting it wrong invisible: a typo resolved to no published rate,
 * so the product was taxed at the fallback on every sale while its invoice looked normal.
 *
 * So this never guesses the code. It makes the right one easy to find: search by plain words
 * ("rice") or by code, always shown with the government's own description, and codes already
 * used in this category offered first, because the second bag of rice belongs where the first
 * one went.
 *
 * Search alone was not enough, though. It only works for a seller who can already name their
 * goods the way the tariff names them - someone selling lungis who does not think of them as
 * "articles of apparel, not knitted" has nothing to type, and the cost of them giving up is a
 * product classified by guesswork. So the tariff can also be walked from the top: pick the
 * trade, then the heading.
 */
const HsnPicker = ({ value, onChange, categoryId, productName, unitPrice, prePackaged }) => {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [selected, setSelected] = useState(null);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  // "search" | "chapters" | "headings" - which way into the tariff is being used.
  const [mode, setMode] = useState("search");
  const [chapters, setChapters] = useState([]);
  const [headings, setHeadings] = useState([]);
  const [activeChapter, setActiveChapter] = useState(null);
  const [browsing, setBrowsing] = useState(false);
  const [mapped, setMapped] = useState(null);
  const boxRef = useRef(null);

  // What the seller has already typed as the product's name is the best description of it
  // anyone is going to get, so it is worth offering against before asking them to describe it
  // a second time in the tariff's vocabulary.
  useEffect(() => {
    let cancelled = false;
    if (value || !productName || productName.trim().length < 3) { setMapped(null); return; }
    const t = setTimeout(() => {
      api.get("/api/hsn/map", {
        params: {
          name: productName.trim(),
          ...(unitPrice ? { unitPrice } : {}),
          ...(prePackaged === true || prePackaged === false ? { prePackaged } : {}),
        },
      })
        .then((r) => { if (!cancelled) setMapped(r.data || null); })
        .catch(() => { if (!cancelled) setMapped(null); });
    }, 400);
    return () => { cancelled = true; clearTimeout(t); };
  }, [productName, unitPrice, prePackaged, value]);

  // Show what the currently saved code actually means, so an existing product's classification
  // can be checked at a glance rather than taken on trust.
  useEffect(() => {
    let cancelled = false;
    if (!value) { setSelected(null); return; }
    api.get(`/api/hsn/${encodeURIComponent(value)}`)
      .then((r) => { if (!cancelled) setSelected({ hsnCode: value, description: r.data?.description }); })
      .catch(() => { if (!cancelled) setSelected({ hsnCode: value, description: null, invalid: true }); });
    return () => { cancelled = true; };
  }, [value]);

  useEffect(() => {
    let cancelled = false;
    api.get("/api/hsn/suggestions", { params: categoryId ? { categoryId } : {} })
      .then((r) => { if (!cancelled) setSuggestions(r.data || []); })
      .catch(() => { if (!cancelled) setSuggestions([]); });
    return () => { cancelled = true; };
  }, [categoryId]);

  // Debounced: the master is 22,478 rows and a seller types faster than it can be searched.
  const runSearch = useCallback((q) => {
    if (!q || q.trim().length < 2) { setResults([]); return; }
    setSearching(true);
    api.get("/api/hsn/search", { params: { q: q.trim() } })
      .then((r) => setResults(r.data || []))
      .catch(() => setResults([]))
      .finally(() => setSearching(false));
  }, []);

  useEffect(() => {
    const t = setTimeout(() => runSearch(query), 250);
    return () => clearTimeout(t);
  }, [query, runSearch]);

  useEffect(() => {
    const onClickAway = (e) => {
      if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", onClickAway);
    return () => document.removeEventListener("mousedown", onClickAway);
  }, []);

  const choose = (row) => {
    onChange(row.hsnCode);
    setSelected(row);
    setQuery("");
    setResults([]);
    setMode("search");
    setOpen(false);
  };

  const browseChapters = () => {
    setMode("chapters");
    if (chapters.length > 0) return;   // the tariff does not change between clicks
    setBrowsing(true);
    api.get("/api/hsn/chapters")
      .then((r) => setChapters(r.data || []))
      .catch(() => setChapters([]))
      .finally(() => setBrowsing(false));
  };

  const openChapter = (chapter) => {
    setActiveChapter(chapter);
    setMode("headings");
    setHeadings([]);
    setBrowsing(true);
    api.get(`/api/hsn/chapters/${encodeURIComponent(chapter.chapter)}/headings`)
      .then((r) => setHeadings(r.data || []))
      .catch(() => setHeadings([]))
      .finally(() => setBrowsing(false));
  };

  // Typing is a clear signal that browsing has been abandoned.
  const onType = (text) => {
    setQuery(text);
    setOpen(true);
    if (text.trim().length > 0 && mode !== "search") setMode("search");
  };

  const shown = query.trim().length >= 2 ? results : suggestions;
  const showingSuggestions = query.trim().length < 2;

  return (
    <div ref={boxRef} style={{ position: "relative" }}>
      <label style={lbl}>HSN Code</label>

      {selected && (
        <div style={{
          display: "flex", justifyContent: "space-between", gap: "0.75rem", alignItems: "flex-start",
          border: `1px solid ${selected.invalid ? "#fecaca" : "#d1fae5"}`,
          background: selected.invalid ? "#fef2f2" : "#f0fdf4",
          borderRadius: 6, padding: "0.5rem 0.65rem", marginBottom: "0.5rem",
        }}>
          <span style={{ fontSize: "0.82rem" }}>
            <strong>{selected.hsnCode}</strong>
            <span style={{ display: "block", color: selected.invalid ? "#b91c1c" : "#4b5563" }}>
              {selected.invalid
                ? "Not a code in the official GSTN master — this product cannot be taxed correctly until it is changed."
                : selected.description}
            </span>
          </span>
          <button type="button" onClick={() => { onChange(""); setSelected(null); setOpen(true); }}
                  style={{ ...linkBtn }}>
            Change
          </button>
        </div>
      )}

      {!selected && mapped?.candidates?.length > 0 && (
        <div style={{
          border: "1px solid #e5e7eb", borderRadius: 8, marginBottom: "0.6rem", overflow: "hidden",
        }}>
          <div style={hdr}>
            Codes published for “{mapped.query}”
            {mapped.translations?.length > 0 && (
              <span style={{ display: "block", fontWeight: 400, marginTop: 2 }}>
                Matched using the tariff’s word{" "}
                {mapped.translations.map((t) => `“${t.officialTerm}” for “${t.term}”`).join(", ")}.
              </span>
            )}
          </div>
          {mapped.candidates.slice(0, 5).map((c) => (
            <button key={c.hsnCode} type="button" onClick={() => choose(c)} style={rowBtn}>
              <span style={{ display: "flex", justifyContent: "space-between", gap: "0.6rem" }}>
                <strong style={{ fontSize: "0.85rem" }}>{c.hsnCode}</strong>
                <span style={{
                  fontSize: "0.78rem", whiteSpace: "nowrap",
                  color: c.taxable ? "#166534" : "#b45309",
                  fontWeight: c.taxable ? 600 : 400,
                }}>
                  {c.taxable ? `${c.gstRate}% GST` : "rate not settled"}
                </span>
              </span>
              <span style={{ display: "block", color: "#4b5563", fontSize: "0.78rem" }}>
                {c.description}
              </span>
              {c.rateNote && (
                <span style={{ display: "block", color: "#6b7280", fontSize: "0.72rem", marginTop: 2 }}>
                  {c.rateNote}
                </span>
              )}
            </button>
          ))}
          <div style={{ ...row, color: "#6b7280", fontSize: "0.72rem", background: "#f9fafb" }}>
            Pick the one that describes your stock — the tariff separates goods by material,
            weave and weight, and only you can tell which yours is.
          </div>
        </div>
      )}

      {(!selected || open) && (
        <>
          <input
            value={query}
            onFocus={() => setOpen(true)}
            onChange={(e) => onType(e.target.value)}
            placeholder="Search by goods or code — e.g. rice, or 1006"
            style={inp}
          />

          {open && (
            <div style={{
              position: "absolute", zIndex: 30, left: 0, right: 0, marginTop: 4,
              background: "#fff", border: "1px solid #e5e7eb", borderRadius: 8,
              boxShadow: "0 8px 24px rgba(0,0,0,0.1)", maxHeight: 280, overflowY: "auto",
            }}>
              {mode === "search" && (
                <>
                  {showingSuggestions && suggestions.length > 0 && (
                    <div style={hdr}>Already used for this category</div>
                  )}
                  {searching && <div style={{ ...row, color: "#6b7280" }}>Searching…</div>}

                  {!searching && shown.length === 0 && (
                    <div style={{ ...row, color: "#6b7280" }}>
                      {showingSuggestions
                        ? "No codes used here yet — type what the product is, such as “rice”."
                        : "No match. Try plainer words, or browse the tariff below."}
                    </div>
                  )}

                  {shown.map((r) => (
                    <button key={r.hsnCode} type="button" onClick={() => choose(r)} style={rowBtn}>
                      <strong style={{ fontSize: "0.85rem" }}>{r.hsnCode}</strong>
                      {typeof r.timesUsed === "number" && (
                        <span style={{ color: "#6b7280", fontSize: "0.72rem", marginLeft: 6 }}>
                          used {r.timesUsed}×
                        </span>
                      )}
                      <span style={{ display: "block", color: "#4b5563", fontSize: "0.78rem" }}>
                        {r.description}
                      </span>
                    </button>
                  ))}

                  <button type="button" onClick={browseChapters} style={{ ...rowBtn, ...footBtn }}>
                    Not sure what to search for? Browse the tariff by trade →
                  </button>
                </>
              )}

              {mode === "chapters" && (
                <>
                  <div style={hdr}>
                    Choose the trade your goods belong to
                    <button type="button" onClick={() => setMode("search")}
                            style={{ ...linkBtn, float: "right", fontWeight: 400 }}>
                      Search instead
                    </button>
                  </div>
                  {browsing && <div style={{ ...row, color: "#6b7280" }}>Loading…</div>}
                  {chapters.map((c) => (
                    <button key={c.chapter} type="button" onClick={() => openChapter(c)} style={rowBtn}>
                      <strong style={{ fontSize: "0.85rem" }}>{c.chapter}</strong>
                      <span style={{ display: "block", color: c.named ? "#4b5563" : "#9ca3af", fontSize: "0.78rem" }}>
                        {c.named ? c.title : "No published title — open it to see the headings"}
                      </span>
                    </button>
                  ))}
                </>
              )}

              {mode === "headings" && (
                <>
                  <div style={hdr}>
                    <button type="button" onClick={() => setMode("chapters")} style={{ ...linkBtn, fontWeight: 400 }}>
                      ← All trades
                    </button>
                    <span style={{ display: "block", marginTop: 2 }}>
                      {activeChapter?.named ? activeChapter.title : `Chapter ${activeChapter?.chapter}`}
                    </span>
                  </div>
                  {browsing && <div style={{ ...row, color: "#6b7280" }}>Loading…</div>}
                  {!browsing && headings.length === 0 && (
                    <div style={{ ...row, color: "#6b7280" }}>
                      No headings listed under this chapter.
                    </div>
                  )}
                  {headings.map((h) => (
                    <button key={h.hsnCode} type="button" onClick={() => choose(h)} style={rowBtn}>
                      <strong style={{ fontSize: "0.85rem" }}>{h.hsnCode}</strong>
                      <span style={{ display: "block", color: "#4b5563", fontSize: "0.78rem" }}>
                        {h.description}
                      </span>
                    </button>
                  ))}
                </>
              )}
            </div>
          )}
        </>
      )}

      <span style={{ display: "block", color: "#6b7280", fontSize: "0.75rem", marginTop: 4 }}>
        Decides the GST charged. The rate itself comes from the CBIC notifications — you only
        choose what the goods are.
      </span>
    </div>
  );
};

const lbl = { fontSize: "0.8rem", fontWeight: 500, display: "block", marginBottom: 4 };
const inp = { width: "100%", padding: "0.5rem", border: "1px solid #d1d5db", borderRadius: 6, fontSize: "0.85rem" };
const hdr = { padding: "0.4rem 0.65rem", fontSize: "0.72rem", fontWeight: 600, color: "#6b7280", background: "#f9fafb" };
const row = { padding: "0.55rem 0.65rem", fontSize: "0.82rem" };
const rowBtn = {
  ...row, display: "block", width: "100%", textAlign: "left",
  background: "none", border: "none", borderTop: "1px solid #f3f4f6", cursor: "pointer",
};
const footBtn = {
  color: "#2563eb", fontSize: "0.78rem", background: "#f9fafb", borderTop: "1px solid #e5e7eb",
};
const linkBtn = {
  background: "none", border: "none", color: "#2563eb", cursor: "pointer",
  fontSize: "0.78rem", padding: 0, whiteSpace: "nowrap",
};

export default HsnPicker;
