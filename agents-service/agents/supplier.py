"""Reads one product from a supplier's Shopify store via the public /products/<handle>.js address.
Only the listed supplier sites are allowed."""

import html
import re
from dataclasses import dataclass, field
from urllib.parse import urlparse

from .http import HttpError, request_json

ALLOWED_HOSTS = {"deodap.in", "www.deodap.in", "baapstore.com", "www.baapstore.com"}
_HANDLE = re.compile(r"/products/([A-Za-z0-9\-_%.]+?)(?:\.js|\.json)?/?$")


class SupplierError(Exception):
    pass


@dataclass
class SupplierProduct:
    url: str
    title: str
    vendor: str
    product_type: str
    description: str
    tags: list = field(default_factory=list)
    price: float | None = None          # rupees
    compare_at_price: float | None = None
    available: bool = False
    image_urls: list = field(default_factory=list)
    sku: str = ""                       # the supplier's own item code (first variant)


def product_js_url(link: str) -> str:
    u = urlparse(link.strip())
    host = (u.hostname or "").lower()
    if u.scheme != "https" or host not in ALLOWED_HOSTS:
        raise SupplierError("Only DeoDap and Baapstore product links are supported.")
    m = _HANDLE.search(u.path or "")
    if not m:
        raise SupplierError("Not a single-product link.")
    return f"https://{host}/products/{m.group(1)}.js"


def html_to_text(s: str) -> str:
    s = re.sub(r"(?i)<br\s*/?>|</p>|</li>|</h[1-6]>", "\n", s or "")
    s = re.sub(r"(?i)<li[^>]*>", "- ", s)
    s = re.sub(r"<[^>]+>", " ", s)
    s = html.unescape(s)
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\s*\n\s*", "\n", s).strip()
    return s[:6000]


def _rupees(paise) -> float | None:
    try:
        v = float(paise)
    except (TypeError, ValueError):
        return None
    return round(v / 100, 2) if v > 0 else None


def fetch(link: str) -> SupplierProduct:
    url = product_js_url(link)
    try:
        p = request_json("GET", url, timeout=30)
    except HttpError as e:
        if e.status == 404:
            raise SupplierError("The supplier says this product no longer exists.") from None
        raise SupplierError(f"The supplier's site did not answer ({e.status}).") from None
    except Exception as e:  # network trouble
        raise SupplierError(f"Could not reach the supplier's site ({type(e).__name__}).") from None
    if not isinstance(p, dict):
        raise SupplierError("The supplier's page could not be read.")
    images = []
    for i in p.get("images") or []:
        s = str(i)
        if s.startswith("//"):
            s = "https:" + s
        if s.startswith("https://"):
            images.append(s)
    return SupplierProduct(
        url=url[:-3],
        title=str(p.get("title") or ""),
        vendor=str(p.get("vendor") or ""),
        product_type=str(p.get("type") or ""),
        description=html_to_text(str(p.get("description") or "")),
        tags=[str(t) for t in (p.get("tags") or [])],
        price=_rupees(p.get("price")),
        compare_at_price=_rupees(p.get("compare_at_price")),
        available=bool(p.get("available")),
        image_urls=images[:8],
        sku=next((str(v.get("sku")).strip() for v in (p.get("variants") or [])
                  if isinstance(v, dict) and v.get("sku")), ""),
    )
