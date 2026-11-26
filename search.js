
// CONNECTO - Search System

async function searchUsers(query) {
    if (!query || !query.trim()) return [];

    const users = await getAllData("users");
    const term = query.trim().toLowerCase();

    return users.filter(user =>
        (user.name || "").toLowerCase().includes(term) ||
        (user.bio || "").toLowerCase().includes(term)
    );
}

async function searchPosts(query) {
    if (!query || !query.trim()) return [];

    const posts = await getAllData("posts");
    const term = query.trim().toLowerCase();

    return posts.filter(post =>
        (post.content || "").toLowerCase().includes(term) ||
        (post.author || "").toLowerCase().includes(term)
    );
}

async function searchAll(query) {
    const [users, posts] = await Promise.all([
        searchUsers(query),
        searchPosts(query)
    ]);

    return {
        users,
        posts,
        total: users.length + posts.length
    };
}

async function getSuggestedUsers(currentUserId) {
    const users = await getAllData("users");

    return users.filter(user =>
        user.userId !== currentUserId
    );
}
