
const $ = (selector) => document.querySelector(selector);

let currentSection = "home";
let currentUser = null;

document.addEventListener("DOMContentLoaded", async () => {
  loadTheme();
  loadCurrentUser();

  try {
    if (typeof initDatabase === "function") {
      await initDatabase();
    }
  } catch (error) {
    console.error("Database:", error);
  }

  showSection("home");

  if ("serviceWorker" in navigator && location.protocol === "https:") {
    navigator.serviceWorker.register("./sw.js").catch(console.error);
  }
});

function loadCurrentUser() {
  try {
    currentUser = JSON.parse(localStorage.getItem("connecto_user") || "null");
  } catch {
    currentUser = null;
  }

  document.querySelectorAll("#userName, .user-name, [data-user-name]")
    .forEach(el => {
      el.textContent = currentUser?.name || "زائر CONNECTO";
    });
}

function showSection(section) {
  currentSection = section;

  document.querySelectorAll(".page, .section").forEach(el => {
    el.classList.remove("active");
    el.classList.add("hidden");
  });

  const target = document.getElementById(section);

  if (target) {
    target.classList.add("active");
    target.classList.remove("hidden");
  }

  document.querySelectorAll("[data-section]").forEach(el => {
    el.classList.toggle("active", el.dataset.section === section);
  });

  if (section === "home") loadPosts();
  if (section === "friends") loadFriends();
  if (section === "notifications") loadNotifications();

  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function publishPost() {
  const input = $("#postContent");
  if (!input) return alert("مربع المنشور غير موجود.");

  const content = input.value.trim();
  if (!content) return alert("اكتب منشور الأول.");

  try {
    if (typeof createPost !== "function") {
      return alert("خدمة المنشورات غير جاهزة.");
    }

    await createPost({
      content,
      author: currentUser?.name || "زائر",
      createdAt: new Date().toISOString()
    });

    input.value = "";
    await loadPosts();
    alert("تم نشر المنشور بنجاح ❤️");
  } catch (error) {
    console.error(error);
    alert("حصل خطأ أثناء النشر.");
  }
}

async function loadPosts() {
  const container = $("#postsContainer");
  if (!container || typeof getPosts !== "function") return;

  try {
    const posts = await getPosts();
    container.replaceChildren();

    if (!posts?.length) {
      container.textContent = "لسه مفيش منشورات. كن أول من ينشر!";
      return;
    }

    [...posts].reverse().forEach(post => {
      const card = document.createElement("article");
      card.className = "post-card";

      const author = document.createElement("h3");
      author.textContent = post.author || "مستخدم CONNECTO";

      const content = document.createElement("p");
      content.textContent = post.content || "";

      const date = document.createElement("small");
      date.textContent = post.createdAt
        ? new Date(post.createdAt).toLocaleString("ar-EG")
        : "";

      card.append(author, content, date);
      container.appendChild(card);
    });
  } catch (error) {
    console.error("Posts:", error);
  }
}

async function loadFriends() {
  const container = $("#friendsContainer");
  if (!container || typeof getFriends !== "function") return;

  try {
    const friends = await getFriends();
    container.replaceChildren();

    if (!friends?.length) {
      container.textContent = "قائمة الأصدقاء فارغة حاليًا.";
      return;
    }

    friends.forEach(friend => {
      const item = document.createElement("div");
      item.className = "friend-item";
      item.textContent = friend.name || "صديق";
      container.appendChild(item);
    });
  } catch (error) {
    console.error(error);
  }
}

async function loadNotifications() {
  const container = $("#notificationsContainer");
  if (!container || typeof getNotifications !== "function") return;

  try {
    const notifications = await getNotifications();
    container.replaceChildren();

    if (!notifications?.length) {
      container.textContent = "لا توجد إشعارات حاليًا.";
      return;
    }

    notifications.forEach(notification => {
      const item = document.createElement("div");
      item.className = "notification-item";
      item.textContent =
        notification.message || notification.text || "إشعار جديد";
      container.appendChild(item);
    });
  } catch (error) {
    console.error(error);
  }
}

function toggleTheme() {
  document.body.classList.toggle("dark-mode");

  localStorage.setItem(
    "connecto_theme",
    document.body.classList.contains("dark-mode") ? "dark" : "light"
  );
}

function loadTheme() {
  if (localStorage.getItem("connecto_theme") === "dark") {
    document.body.classList.add("dark-mode");
  }
}

function logout() {
  if (!confirm("هل تريد تسجيل الخروج؟")) return;

  localStorage.removeItem("connecto_user");
  currentUser = null;
  location.reload();
}

window.showSection = showSection;
window.publishPost = publishPost;
window.toggleTheme = toggleTheme;
window.logout = logout;
window.loadPosts = loadPosts;
