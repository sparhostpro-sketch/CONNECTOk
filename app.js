
const $ = (selector) => document.querySelector(selector);

let currentSection = "home";
let currentUser = null;

document.addEventListener("DOMContentLoaded", async () => {
  try {
    if (typeof initDatabase === "function") {
      await initDatabase();
    }
  } catch (error) {
    console.error("Database error:", error);
  }

  loadCurrentUser();
  showSection("home");

  if ("serviceWorker" in navigator && location.protocol === "https:") {
    navigator.serviceWorker.register("./sw.js").catch(console.error);
  }
});

function loadCurrentUser() {
  try {
    const saved = localStorage.getItem("connecto_user");
    currentUser = saved ? JSON.parse(saved) : null;
  } catch {
    currentUser = null;
  }

  const name = currentUser?.name || "زائر CONNECTO";
  const nameElements = document.querySelectorAll(
    "#userName, .user-name, [data-user-name]"
  );

  nameElements.forEach((element) => {
    element.textContent = name;
  });
}

function showSection(section) {
  currentSection = section;

  document.querySelectorAll(".section").forEach((element) => {
    element.classList.add("hidden");
  });

  const target = document.getElementById(section);

  if (target) {
    target.classList.remove("hidden");
  }

  document.querySelectorAll("[data-section]").forEach((element) => {
    element.classList.toggle(
      "active",
      element.dataset.section === section
    );
  });

  const title = document.getElementById("pageTitle");

  if (title) {
    const titles = {
      home: "الرئيسية",
      friends: "الأصدقاء",
      messages: "الرسائل",
      notifications: "الإشعارات",
      profile: "الملف الشخصي",
      settings: "الإعدادات",
    };

    title.textContent = titles[section] || "CONNECTO";
  }

  if (section === "home") loadPosts();
  if (section === "friends") loadFriends();
  if (section === "notifications") loadNotifications();

  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function publishPost() {
  const input =
    $("#postText") ||
    $("#postContent") ||
    $("#newPost");

  if (!input) {
    alert("مربع كتابة المنشور غير موجود.");
    return;
  }

  const content = input.value.trim();

  if (!content) {
    alert("اكتب حاجة قبل النشر.");
    return;
  }

  if (typeof createPost !== "function") {
    alert("خدمة المنشورات غير جاهزة.");
    return;
  }

  try {
    await createPost({
      content,
      author: currentUser?.name || "زائر",
      createdAt: new Date().toISOString(),
    });

    input.value = "";
    await loadPosts();
    alert("تم نشر المنشور بنجاح.");
  } catch (error) {
    console.error(error);
    alert("حصل خطأ أثناء نشر المنشور.");
  }
}

async function loadPosts() {
  const container =
    $("#postsContainer") ||
    $("#postsList") ||
    $("#posts");

  if (!container) return;

  try {
    if (typeof getPosts !== "function") return;

    const posts = await getPosts();

    if (!posts || posts.length === 0) {
      container.innerHTML =
        '<p class="empty-state">لسه مفيش منشورات. كن أول من ينشر!</p>';
      return;
    }

    container.replaceChildren();

    posts.slice().reverse().forEach((post) => {
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
    console.error("Loading posts failed:", error);
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

    friends.forEach((friend) => {
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

    notifications.forEach((notification) => {
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

  const enabled = document.body.classList.contains("dark-mode");
  localStorage.setItem("connecto_theme", enabled ? "dark" : "light");
}

function loadTheme() {
  const theme = localStorage.getItem("connecto_theme");

  if (theme === "dark") {
    document.body.classList.add("dark-mode");
  }
}

loadTheme();

function logout() {
  const confirmed = confirm("هل تريد تسجيل الخروج؟");
  if (!confirmed) return;

  localStorage.removeItem("connecto_user");
  currentUser = null;
  location.reload();
}

window.showSection = showSection;
window.publishPost = publishPost;
window.toggleTheme = toggleTheme;
window.logout = logout;
window.loadPosts = loadPosts;
