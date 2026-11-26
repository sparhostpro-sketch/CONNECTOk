
function showMessage() {
    alert("أهلاً بك في CONNECTO ❤️ قريباً هتقدر تتواصل مع العالم كله!");
}

// التأكد من تحميل الموقع
document.addEventListener("DOMContentLoaded", function () {
    console.log("CONNECTO is running!");

    const button = document.querySelector(".welcome button");

    if (button) {
        button.addEventListener("click", function () {
            console.log("Welcome to CONNECTO");
        });
    }
});
