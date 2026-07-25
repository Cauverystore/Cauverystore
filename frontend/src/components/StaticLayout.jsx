import React from "react";
import "../styles/staticLayout.css";

const StaticLayout = ({ hero, tabs, activeTab, onTabChange, children, maxWidth }) => {
  return (
    <div className="static-page">
      {hero && (
        <div className="static-hero" style={hero.style || {}}>
          <div className="static-hero-content">
            <h1>{hero.title}</h1>
            {hero.subtitle && <p>{hero.subtitle}</p>}
            {hero.actions && <div className="static-hero-actions">{hero.actions}</div>}
          </div>
        </div>
      )}

      {tabs && tabs.length > 0 && (
        <div className="static-tabs">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              className={`static-tab${activeTab === tab.id ? " active" : ""}`}
              onClick={() => onTabChange(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}

      <div className="static-content" style={maxWidth ? { maxWidth } : {}}>
        {children}
      </div>
    </div>
  );
};
export default StaticLayout;
