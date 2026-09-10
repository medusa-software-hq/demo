const page = (hostname) => `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>demo</title>
  </head>
  <body>
    <h1>Hello from ${hostname}</h1>
    <p>Served by a Cloudflare Worker this repository deploys.</p>
  </body>
</html>
`;

export default {
  fetch(request) {
    // The hostname distinguishes the environments, so one source serves both without
    // anything here needing to know which it is.
    const { hostname } = new URL(request.url);
    return new Response(page(hostname), {
      headers: { 'content-type': 'text/html; charset=utf-8' },
    });
  },
};
