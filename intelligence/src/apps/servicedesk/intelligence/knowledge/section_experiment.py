"""Separate chunk-policy experiment over the frozen enriched projection."""

from hdpi.evaluation.candidates import TfidfCandidate
from hdpi.retrieval.section_chunking import SECTION_POLICY, section_chunks
from hdpi.retrieval.tfidf.engine import fit_chunks

from .experiments import compare_corpora, evaluate_family_control


class SectionCandidate(TfidfCandidate):
    def __init__(self):
        super().__init__()
        self.system['chunkPolicyVersion'] = SECTION_POLICY

    def build(self, sources):
        chunks = []
        for identifier, version, checksum, text in sources:
            chunks.extend(
                section_chunks(text, source_id=identifier, source_state=(version, checksum))
            )
        return fit_chunks(tuple(chunks))


def compare_chunking(root, directory):
    paragraph = compare_corpora(root, directory)['A2']
    section = evaluate_family_control(
        root,
        directory,
        enriched=True,
        reference=paragraph,
        candidate=SectionCandidate(),
        experiment_id='A3',
    )
    return {'A2': paragraph, 'A3': section}
