import unittest
from prepare_brand_demo import prepare, seed_score
from evaluate_brand_recommend import metrics, rank_score


class PreparationTests(unittest.TestCase):
    def test_seed_gold_matches_spec(self):
        row = dict(successful_runs=120, supplier_failures=5, review_count=25, average_rating=4.6)
        actual = seed_score(row)
        self.assertEqual(actual["success_rate"], .96)
        self.assertEqual(actual["tier"], 2)
        self.assertAlmostEqual(actual["score"], 71.01, delta=.1)

    def test_cold_start_is_not_zero_success(self):
        actual = seed_score(dict(successful_runs=0, supplier_failures=0, review_count=0, average_rating=None))
        self.assertEqual(actual["success_rate"], 1)
        self.assertEqual(actual["tier"], 4)

    def test_hurdle_prevents_platinum_with_few_reviews(self):
        actual = seed_score(dict(successful_runs=999, supplier_failures=0, review_count=25, average_rating=5))
        self.assertGreater(actual["score"], 80)
        self.assertEqual(actual["tier"], 2)

    def test_bad_seed_values_are_rejected(self):
        for values in ((-1, 0, 0, None), (0, 0, 3, 6), (1.5, 0, 0, None)):
            with self.assertRaises(ValueError):
                seed_score(dict(zip(("successful_runs", "supplier_failures", "review_count", "average_rating"), values)))

    def test_preparation_contract_and_determinism(self):
        pack = prepare()
        self.assertEqual(pack, prepare())
        agents = pack["brand_agents_local.json"]["agents"]
        self.assertEqual(len(agents), 4)
        self.assertEqual(len({a["owner_id"] for a in agents}), 3)
        self.assertIsNone(agents[-1]["input_schema_id"])
        self.assertNotIn("input_ports", agents[-1])
        self.assertTrue(all(a["output_schema_id"].endswith("@1") for a in agents))

    def test_rank_eight_is_hit_nine_is_miss(self):
        result = metrics([{"rank": n} for n in (1, 8, 9, None)])
        self.assertEqual(result["top1"], 1)
        self.assertEqual(result["top8"], 2)
        with self.assertRaises(ValueError):
            metrics([])

    def test_gated_trust_cannot_rescue_irrelevant_agent(self):
        high_trust = rank_score(0, None, "gated", .3, 1)
        relevant_bronze = rank_score(.4, None, "gated", .43, .5)
        self.assertEqual(high_trust, 0)
        self.assertGreater(relevant_bronze, high_trust)
        self.assertEqual(rank_score(.2, None, "additive", .29, .5), .29)


if __name__ == "__main__":
    unittest.main()
