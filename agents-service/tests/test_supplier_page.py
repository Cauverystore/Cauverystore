import unittest

from agents.supplier import origin_from_page


class OriginFromPageTest(unittest.TestCase):
    def test_deodap_style(self):
        page = "<p>Not for dishwashers.</p><p>Country Of Origin :- India</p><p>GST :- 18% (Included in price)</p>"
        self.assertEqual(origin_from_page(page), "India")

    def test_colon_and_other_country(self):
        self.assertEqual(origin_from_page("<b>Country of origin</b>: china | MRP 299"), "China")

    def test_ignores_scripts(self):
        page = '<script>var a = "Country of Origin: China GST";</script><div>Country Of Origin :- India</div><span>GST</span>'
        self.assertEqual(origin_from_page(page), "India")

    def test_not_stated(self):
        self.assertEqual(origin_from_page("<p>Lovely egg box</p>"), "")
        self.assertEqual(origin_from_page(""), "")


if __name__ == "__main__":
    unittest.main()
