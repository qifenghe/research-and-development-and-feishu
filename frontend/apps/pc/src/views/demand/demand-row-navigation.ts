export function createDemandRowProps(
  id: string,
  openDetail: (id: string) => void,
) {
  return {
    onClick: () => openDetail(id),
    style: { cursor: "pointer" },
  };
}
