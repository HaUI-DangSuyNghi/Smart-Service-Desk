/** Light lettering on dark backgrounds; dark lettering on the white navigation. */
export const Wordmark = ({ light }: { light: boolean }) => {
  return (
    <img
      src={light ? "/logo-svd-light-removebg-preview.png" : "/logo-svd-dark-removebg-preview.png"}
      alt="Smart Service Desk"
      width={666}
      height={375}
      className="h-12 w-52 shrink-0 object-cover object-center"
    />
  );
};
