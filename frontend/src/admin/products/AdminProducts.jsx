import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/axios";
import Pagination from "../../components/Pagination";
import "../../styles/adminProducts.css";

const AdminProducts = () => {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState([]);
  const navigate = useNavigate();

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const res = await api.get("/api/admin/products", { params: { page, search } });
        setProducts(res.data.content || res.data || []);
        setTotalPages(res.data.totalPages || 1);
      } catch (err) { console.error(err); }
      setLoading(false);
    };
    fetch();
  }, [page, search]);

  const toggleSelect = (id) => setSelected(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);

  const handleBulkDelete = async () => {
    try {
      await api.post("/api/admin/products/bulk-delete", { ids: selected });
      setSelected([]);
      setPage(1);
      const res = await api.get("/api/admin/products", { params: { page: 1 } });
      setProducts(res.data.content || res.data || []);
    } catch (err) { alert("Failed to delete"); }
  };

  const handleDelete = async (id) => {
    try {
      await api.delete(`/api/admin/products/${id}`);
      setProducts(products.filter(p => (p.id || p._id) !== id));
    } catch (err) { alert("Failed to delete"); }
  };

  if (loading) return <div style={{ textAlign: "center", padding: "3rem" }}>Loading...</div>;

  return (
    <div className="admin-products-page">
      <div className="admin-products-header">
        <h1>Products</h1>
        <button onClick={() => navigate("/admin/products/add")} style={{ padding: "0.5rem 1.25rem", background: "#16a34a", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer" }}>Add Product</button>
      </div>

      <div className="admin-filters">
        <input placeholder="Search..." value={search} onChange={(e) => { setSearch(e.target.value); setPage(1); }} />
        {selected.length > 0 && <button onClick={handleBulkDelete} style={{ padding: "0.3rem 0.75rem", border: "1px solid #dc2626", color: "#dc2626", borderRadius: 4, background: "#fff", cursor: "pointer" }}>Delete ({selected.length})</button>}
      </div>

      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th><input type="checkbox" onChange={(e) => e.target.checked ? setSelected(products.map(p => p.id || p._id)) : setSelected([])} /></th>
              <th>Image</th><th>Name</th><th>Category</th><th>Price</th><th>Stock</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.id || p._id}>
                <td><input type="checkbox" checked={selected.includes(p.id || p._id)} onChange={() => toggleSelect(p.id || p._id)} /></td>
                <td><img src={p.image || p.images?.[0] || "/images/placeholder.svg"} alt="" style={{ width: 40, height: 40, objectFit: "cover", borderRadius: 4 }} /></td>
                <td>{p.name}</td><td>{p.category?.name || p.category || ""}</td><td>&#8377;{(p.price || p.dealPrice || 0).toFixed(2)}</td><td>{p.stock ?? 0}</td>
                <td className="table-actions">
                  <button className="btn-edit" onClick={() => navigate(`/admin/products/edit/${p.id || p._id}`)}>Edit</button>
                  <button className="btn-delete" onClick={() => handleDelete(p.id || p._id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={totalPages} onPage={setPage} />
    </div>
  );
};
export default AdminProducts;
