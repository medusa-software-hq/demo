/** The page this app serves, named by the hostname it was reached on. */
export const page = (hostname: string): string => `<!doctype html>
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
