import React from 'react';

const PageLayout = ({ title, subtitle, actions, children, className = '' }) => {
  return (
    <div className={`page ${className}`}>
      {(title || actions) && (
        <div className="page-header">
          <div>
            {title && <h1 className="page-title">{title}</h1>}
            {subtitle && <p className="page-subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="page-actions">{actions}</div>}
        </div>
      )}
      {children}
    </div>
  );
};

export default PageLayout;
