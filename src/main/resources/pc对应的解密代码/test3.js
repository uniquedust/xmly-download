const CryptoJS = require("crypto-js");

function decrypt(t) {
  return CryptoJS.AES.decrypt({
      ciphertext: CryptoJS.enc.Base64url.parse(t)
  }, CryptoJS.enc.Hex.parse('aaad3e4fd540b0f79dca95606e72bf93'), {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7
  }).toString(CryptoJS.enc.Utf8)
}

var a="pX7rCko1ZPLJXbyU3qjcDqAp042BK5yCrhhNlUZEBd6lHKILemhbvHD1YkhQ7FDbR8oy0iWyTecaq-rqUqF4QgK6Yq71MGvfUu527Y6Lh3-pGhOMwCaqKFcKmAMjd_YwSWFWJPkA7IyMIkUFnFT6iFveD6nNPzeeFp_tLXcAcwjkaY7hzCkggIPQQYfi8_2YIIPhpEaBXz26c2lKq7vS72_pp9Vb9GgSgezuKF1AZhOj6HE0jqbh3vEXX-D9ixU5Tx7IsucyqYey-IeolUFJ5OpZuBgVHmSTF6IVtQea13s";
console.log(decrypt(a));