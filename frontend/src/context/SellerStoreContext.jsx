import React, { createContext, useCallback, useContext, useEffect, useState } from "react";
import api from "../api/axios";

// Which seller store the seller panel is working in, and what the signed-in person may do there.
// The chosen store is remembered in localStorage and sent as X-Seller-Id by the axios helper.
export const STORE_KEY = "cs_seller_store";
const SellerStoreContext = createContext(null);

const readStore = () => { try { return localStorage.getItem(STORE_KEY); } catch { return null; } };
const writeStore = (id) => {
  try {
    if (id == null) localStorage.removeItem(STORE_KEY);
    else localStorage.setItem(STORE_KEY, String(id));
  } catch { /* storage blocked */ }
};

export const SellerStoreProvider = ({ children }) => {
  const [stores, setStores] = useState([]);
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = (await api.get("/api/seller/team/my-stores")).data || [];
      setStores(list);
      const saved = readStore();
      // A saved store this person is no longer on is dropped, so they are not locked out.
      let pick = list.find((s) => String(s.sellerId) === saved) || list.find((s) => s.owner) || list[0] || null;
      if (pick && pick.owner) writeStore(null);
      else writeStore(pick ? pick.sellerId : null);
      setCurrent(pick);
    } catch {
      setStores([]);
      setCurrent(null);
    }
    setLoading(false);
  }, []);

  useEffect(() => { load(); }, [load]);

  const switchStore = (sellerId) => {
    const s = stores.find((x) => x.sellerId === sellerId);
    if (!s) return;
    writeStore(s.owner ? null : s.sellerId);
    // Every page reads from the chosen store, so start fresh rather than mixing two stores' data.
    window.location.assign("/seller/dashboard");
  };

  const can = (permission) => !!current && (current.owner || (current.permissions || []).includes(permission));

  return (
    <SellerStoreContext.Provider value={{ stores, current, loading, can, switchStore, reload: load }}>
      {children}
    </SellerStoreContext.Provider>
  );
};

export const useSellerStore = () => useContext(SellerStoreContext) || { stores: [], current: null, loading: false, can: () => true, switchStore: () => {}, reload: () => {} };
