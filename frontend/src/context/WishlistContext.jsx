import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { getWishlist, addToWishlist, removeFromWishlist } from "../services/wishlistService";

const WishlistContext = createContext({ ids: new Set(), loaded: false, toggle: () => {}, refresh: () => {} });

const extractProductId = (item) => item.product?.id || item.product?._id || item.productId || item.id || item._id;

export const WishlistProvider = ({ children }) => {
  const [ids, setIds] = useState(new Set());
  const [loaded, setLoaded] = useState(false);

  const refresh = useCallback(async () => {
    const token = localStorage.getItem("accessToken");
    if (!token) { setIds(new Set()); setLoaded(true); return; }
    try {
      const { data } = await getWishlist();
      setIds(new Set((data || []).map(extractProductId).filter(Boolean)));
    } catch { /* ignore */ }
    setLoaded(true);
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  const toggle = useCallback(async (productId) => {
    if (!productId) return;
    const isWishlisted = ids.has(productId);
    try {
      if (isWishlisted) {
        await removeFromWishlist(productId);
        setIds(prev => { const next = new Set(prev); next.delete(productId); return next; });
      } else {
        await addToWishlist(productId);
        setIds(prev => new Set(prev).add(productId));
      }
    } catch { /* ignore */ }
  }, [ids]);

  return (
    <WishlistContext.Provider value={{ ids, loaded, toggle, refresh }}>
      {children}
    </WishlistContext.Provider>
  );
};

export const useWishlist = () => useContext(WishlistContext);
