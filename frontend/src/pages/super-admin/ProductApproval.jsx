import React, { useState, useEffect } from "react";
import { Package } from "lucide-react";
import api from "../../utils/axios";
import { useToast } from "../../admin/context/ToastContext";

const ProductApproval = () => {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actioning, setActioning] = useState(null);
  const { showToast } = useToast();

  const fetchProducts = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await api.get("/api/admin/products/all");
      const all = Array.isArray(res.data) ? res.data : [];
      setProducts(all.filter(p => (p.approvalStatus || p.productStatus || '').toLowerCase() === "pending"));
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load products');
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchProducts(); }, []);

  const handleAction = async (id, status) => {
    setActioning(id);
    try {
      await api.put(`/api/admin/products/${id}/approval`, { approvalStatus: status });
      showToast(`Product ${status === "approved" ? "approved" : "rejected"} successfully`, "success");
      fetchProducts();
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to update product status', "error");
    } finally {
      setActioning(null);
    }
  };

  if (loading) {
    return (
      <div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem' }}>Product Approval Queue</h1>
        <div className="admin-skeleton-row">
          {[1,2,3,4].map(i => <div key={i} className="admin-skeleton-card" style={{ height:'48px' }} />)}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Product Approval Queue</h1>
      </div>

      {error && <div className="admin-alert error">{error}</div>}

      <div className="admin-table-wrapper">
        {products.length === 0 ? (
          <div className="admin-empty-state">
            <div className="admin-empty-state-icon"><Package size={32} /></div>
            <div className="admin-empty-state-text">No products pending approval</div>
          </div>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Seller</th>
                <th>Price</th>
                <th>Stock</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map(p => (
                <tr key={p.id || p._id}>
                  <td style={{ fontWeight: 500 }}>{p.name || p.productName || '-'}</td>
                  <td style={{ color: '#6b7280' }}>{p.sellerName || p.sellerId || 'N/A'}</td>
                  <td>₹{(p.price ?? 0).toLocaleString()}</td>
                  <td>{p.stock ?? 0}</td>
                  <td>
                    <span className="admin-badge pending">{p.approvalStatus || p.productStatus || 'PENDING'}</span>
                  </td>
                  <td>
                    <div className="admin-table-actions-cell">
                      <button
                        className="admin-btn admin-btn-sm admin-btn-success"
                        onClick={() => handleAction(p.id || p._id, "approved")}
                        disabled={actioning === (p.id || p._id)}
                      >
                        {actioning === (p.id || p._id) ? '...' : 'Approve'}
                      </button>
                      <button
                        className="admin-btn admin-btn-sm admin-btn-danger"
                        onClick={() => handleAction(p.id || p._id, "rejected")}
                        disabled={actioning === (p.id || p._id)}
                      >
                        {actioning === (p.id || p._id) ? '...' : 'Reject'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div className="admin-pagination">
          <div className="admin-pagination-info">{products.length} product(s) pending</div>
        </div>
      </div>
    </div>
  );
};

export default ProductApproval;
