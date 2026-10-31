import type { Collision, CollisionDetection } from "@dnd-kit/core";

/** Thẻ đè lên ít nhất nửa bề ngang cột thì tính là đã sang cột đó. */
const MIN_OVERLAP = 0.5;

/**
 * Chỉ xét bề ngang: cột cao thấp khác nhau (cột trống rất ngắn) nên thẻ ở dưới đáy cột vẫn tính.
 * Thẻ nằm hẳn phía trên cột (kéo lên thanh công cụ) thì không tính.
 */
export const halfColumnOverlap: CollisionDetection = ({
  collisionRect,
  droppableContainers,
  droppableRects,
}) => {
  const matches: { collision: Collision; ratio: number }[] = [];
  for (const container of droppableContainers) {
    const rect = droppableRects.get(container.id);
    if (!rect || rect.width === 0 || collisionRect.bottom <= rect.top) continue;
    const overlap =
      Math.min(collisionRect.right, rect.right) - Math.max(collisionRect.left, rect.left);
    const ratio = overlap / rect.width;
    if (ratio >= MIN_OVERLAP) {
      matches.push({
        collision: { id: container.id, data: { droppableContainer: container, value: ratio } },
        ratio,
      });
    }
  }
  // Đè lên hai cột cùng lúc thì chọn cột bị đè nhiều hơn
  return matches.sort((a, b) => b.ratio - a.ratio).map((match) => match.collision);
};
