// CONNECTO - Posts System

async function createPost(author, content) {
    if (!content || !content.trim()) {
        throw new Error("اكتب شيئاً قبل نشر المنشور");
    }

    const post = {
        author: author || "مستخدم CONNECTO",
        content: content.trim(),
        likes: [],
        comments: [],
        createdAt: new Date().toISOString()
    };

    return await saveData("posts", post);
}

async function getFeed() {
    const posts = await getAllData("posts");

    return posts.sort((a, b) =>
        new Date(b.createdAt) - new Date(a.createdAt)
    );
}

async function toggleLike(postId, userId) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("posts", "readwrite");
        const store = transaction.objectStore("posts");
        const request = store.get(postId);

        request.onsuccess = function() {
            const post = request.result;

            if (!post) {
                reject(new Error("المنشور غير موجود"));
                return;
            }

            post.likes = post.likes || [];

            const index = post.likes.indexOf(userId);

            if (index === -1) {
                post.likes.push(userId);
            } else {
                post.likes.splice(index, 1);
            }

            store.put(post);
            resolve(post.likes.length);
        };

        request.onerror = () => reject(request.error);
    });
}

async function addComment(postId, author, content) {
    if (!content || !content.trim()) {
        throw new Error("اكتب تعليقاً أولاً");
    }

    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("posts", "readwrite");
        const store = transaction.objectStore("posts");
        const request = store.get(postId);

        request.onsuccess = function() {
            const post = request.result;

            if (!post) {
                reject(new Error("المنشور غير موجود"));
                return;
            }

            post.comments = post.comments || [];

            post.comments.push({
                author: author || "مستخدم CONNECTO",
                content: content.trim(),
                createdAt: new Date().toISOString()
            });

            store.put(post);
            resolve(post);
        };

        request.onerror = () => reject(request.error);
    });
}
