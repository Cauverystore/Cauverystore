import React from "react";
import "../styles/aboutUs.css";

const AboutUs = () => (
  <div className="about-page">
    <h1>About Cauvery Store</h1>
    <div className="about-content">
      <p>Welcome to Cauvery Store, your premier destination for quality products at affordable prices. We are committed to providing an exceptional shopping experience with a wide range of products across multiple categories.</p>
      <h2>Our Mission</h2>
      <p>To connect customers with quality products while empowering local sellers and businesses. We believe in creating a sustainable e-commerce ecosystem that benefits everyone.</p>
      <h2>Our Vision</h2>
      <p>To become the most trusted and loved shopping destination, offering unparalleled choice, convenience, and value to our customers.</p>
      <div className="about-features">
        <div className="about-feature"><h3>Wide Selection</h3><p>Thousands of products across multiple categories including electronics, fashion, home, and more.</p></div>
        <div className="about-feature"><h3>Best Prices</h3><p>Competitive pricing with regular discounts and offers to ensure you get the best value.</p></div>
        <div className="about-feature"><h3>Fast Delivery</h3><p>Quick and reliable shipping to get your products to your doorstep as soon as possible.</p></div>
        <div className="about-feature"><h3>Secure Payments</h3><p>Multiple payment options with industry-standard security for worry-free transactions.</p></div>
      </div>
    </div>
  </div>
);
export default AboutUs;
