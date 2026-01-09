-- Create a view that shows only verified profiles
-- This view joins the profiles table with auth.users table
-- and filters to show only users who have confirmed their email

CREATE OR REPLACE VIEW verified_profiles AS
SELECT
  p.id,
  p.email,
  p.full_name,
  p.phone,
  p.avatar_url,
  p.created_at,
  p.updated_at
FROM profiles p
INNER JOIN auth.users u ON p.id = u.id
WHERE u.email_confirmed_at IS NOT NULL
ORDER BY p.full_name;

-- Grant access to the view for authenticated users
GRANT SELECT ON verified_profiles TO authenticated;
GRANT SELECT ON verified_profiles TO anon;

-- Optional: Add a comment to explain the view
COMMENT ON VIEW verified_profiles IS 'Shows only profiles for users who have verified their email address';
